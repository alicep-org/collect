/*
 * Copyright 2018 Palantir Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.alicep.collect.benchmark;

import static java.lang.management.ManagementFactory.getGarbageCollectorMXBeans;
import static java.lang.management.ManagementFactory.getMemoryPoolMXBeans;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;

import javax.annotation.Nullable;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

import com.google.common.collect.ImmutableSet;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

public abstract class MemoryAllocationMonitor {

  private static final MemoryAllocationMonitor instance = create();

  public static MemoryAllocationMonitor get() {
    return instance;
  }

  public abstract void warnIfMonitoringDisabled();

  public abstract void prepareForBenchmark();

  public abstract long memoryUsed();

  public abstract long approximateBaselineError();

  private static MemoryAllocationMonitor create() {
    List<MemoryPoolMXBean> pools = parallelSweepPools();
    if (pools.isEmpty()) {
      return new DisabledMemoryAllocationMonitor(ImmutableSet.of());
    }
    Set<String> missingCollectors = collectorsWeCannotRun();
    if (missingCollectors.isEmpty()) {
      return new ParallelSweepMemoryAllocationMonitor();
    } else {
      return new DisabledMemoryAllocationMonitor(missingCollectors);
    }
  }

  private static List<MemoryPoolMXBean> parallelSweepPools() {
    return getMemoryPoolMXBeans()
        .stream()
        .filter(pool -> pool.getName().startsWith("PS "))
        .collect(toList());
  }

  private static Set<String> collectorsWeCannotRun() {
    // Make sure all our allocations are done _before_ we get the collection counts.
    GarbageCollectorMXBean[] gcBeans = getGarbageCollectorMXBeans().toArray(new GarbageCollectorMXBean[0]);
    long[] countsBefore = new long[gcBeans.length];
    long[] countsAfter = new long[gcBeans.length];

    for (int i = 0; i < gcBeans.length; ++i) {
      countsBefore[i] = gcBeans[i].getCollectionCount();
    }

    System.gc();

    for (int i = 0; i < gcBeans.length; ++i) {
      countsAfter[i] = gcBeans[i].getCollectionCount();
    }

    Set<String> failedCollections = new TreeSet<>();
    for (int i = 0; i < gcBeans.length; ++i) {
      if (countsBefore[i] >= countsAfter[i]) {
        failedCollections.add(gcBeans[i].getName());
      }
    }

    return failedCollections;
  }

  private static Optional<MemoryPoolMXBean> getMemoryPoolBean(String name) {
    return getMemoryPoolMXBeans()
        .stream()
        .filter(bean -> bean.getName().equals(name))
        .findAny();
  }

  private static class SweepCount {

    private final Lock lock = new ReentrantLock();
    private final Condition sweeped = lock.newCondition();
    private volatile long sweeps = 0;

    public void recordSweep() {
      lock.lock();
      sweeps++;
      sweeped.signalAll();
      lock.unlock();
    }

    public long currentSweeps() {
      return sweeps;
    }

    public void awaitSweeps(long targetSweeps) {
      lock.lock();
      try {
        while (sweeps < targetSweeps) {
          if (!sweeped.await(100, MILLISECONDS)) {
            return;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } finally {
        lock.unlock();
      }
    }
  }

  private static class ParallelSweepMemoryAllocationMonitor extends MemoryAllocationMonitor {

    @SuppressWarnings("restriction")
    private static void registerPSCollectionListener(LongConsumer listener) {
      getGarbageCollectorMXBeans()
          .stream()
          .map(bean -> (NotificationEmitter) bean)
          .forEach(collector -> collector.addNotificationListener((notification, handback) -> {
            if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
              CompositeData data = (CompositeData) notification.getUserData();
              GcInfo info = GarbageCollectionNotificationInfo.from(data).getGcInfo();
              long usageBefore = psUsage(info.getMemoryUsageBeforeGc());
              long usageAfter = psUsage(info.getMemoryUsageAfterGc());
              listener.accept(usageBefore - usageAfter);
            }
          }, null, null));
    }

    private static long psUsage(Map<String, MemoryUsage> usage) {
      long total = 0;
      for (Entry<String, MemoryUsage> entry : usage.entrySet()) {
        if (entry.getKey().startsWith("PS ")) {
          total += entry.getValue().getUsed();
        }
      }
      return total;
    }


    @Nullable private final MemoryPoolMXBean survivorSpace;
    private final SweepCount sweeps = new SweepCount();
    private volatile long reclaimed;
    private long baselineError = Long.MAX_VALUE;

    public ParallelSweepMemoryAllocationMonitor() {
      survivorSpace = getMemoryPoolBean("PS Survivor Space").orElse(null);
      registerPSCollectionListener(reclaimedThisCollection -> {
        reclaimed += reclaimedThisCollection;
        sweeps.recordSweep();
      });
    }

    @Override
    public void warnIfMonitoringDisabled() { }

    @Override
    public void prepareForBenchmark() {
      int i = 0;
      do {
        sweepAndAwait();
        ++i;
      } while (survivorSpace != null && survivorSpace.getUsage().getUsed() > 0 && i < 100);
    }

    @Override
    public long memoryUsed() {
      sweepAndAwait();
      return reclaimed;
    }

    /**
     * Determine the common baseline error in this monitor. Subtracting this from any given sample
     * will remove some of the bias from the result.
     *
     * <p>Sometimes the error is higher; frequently it is lower. Sample until the first quartile
     * and the median agree.
     */
    @Override
    public long approximateBaselineError() {
      if (baselineError == Long.MAX_VALUE) {
        prepareForBenchmark();
        long[] samples = new long[7];
        int start = 0;
        do {
          long lastMemoryUsed = memoryUsed();
          for (int i = start; i < samples.length; ++i) {
            long memoryUsed = memoryUsed();
            samples[i] = memoryUsed - lastMemoryUsed;
            lastMemoryUsed = memoryUsed;
          }
          Arrays.sort(samples);
        } while (samples[samples.length / 4] != samples[samples.length / 2]);
        baselineError = samples[samples.length / 2];
      }
      return baselineError;
    }

    private void sweepAndAwait() {
      long sweepsBeforeGc = sweeps.currentSweeps();
      System.gc();
      sweeps.awaitSweeps(sweepsBeforeGc + 2);
    }
  }

  private static class DisabledMemoryAllocationMonitor extends MemoryAllocationMonitor {

    private final Set<String> missingCollectors;

    DisabledMemoryAllocationMonitor(Set<String> missingCollectors) {
      this.missingCollectors = missingCollectors;
    }

    @Override
    public void warnIfMonitoringDisabled() {
      if (missingCollectors.isEmpty()) {
        System.out.println("[WARN] Not using parallel sweep garbage collection");
      } else {
        System.out.println(missingCollectors.stream().collect(joining(", ", "[WARN] Could not collect ", "")));
      }
      System.out.println("  - Memory allocation information will not be available");
      if (missingCollectors.contains("PS MarkSweep")) {
        System.out.println("  - Try rerunning with -XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses");
      }
    }

    @Override
    public void prepareForBenchmark() {
      // Do our best
      System.gc();
    }

    @Override
    public long memoryUsed() {
      return -1;
    }

    @Override
    public long approximateBaselineError() {
      return -1;
    }
  }
}
