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
import static java.util.stream.Collectors.joining;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

public abstract class MemoryAllocationMonitor {

  public static MemoryAllocationMonitor get() {
    Set<String> missingCollectors = collectorsWeCannotRun();
    if (missingCollectors.isEmpty()) {
      return new ActiveMemoryAllocationMonitor();
    } else {
      return new DisabledMemoryAllocationMonitor(missingCollectors);
    }
  }

  public abstract void warnIfMonitoringDisabled();

  public abstract void reset();

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

  private static class ActiveMemoryAllocationMonitor extends MemoryAllocationMonitor {

    @Nullable private final MemoryPoolMXBean survivorSpaceBean;

    public ActiveMemoryAllocationMonitor() {
      survivorSpaceBean = ManagementFactory.getMemoryPoolMXBeans()
          .stream()
          .filter(bean -> bean.getName().equals("PS Survivor Space"))
          .findAny()
          .orElse(null);
    }

    @Override
    public void warnIfMonitoringDisabled() { }

    @Override
    public void reset() {
      int i = 0;
      do {
        System.gc();
        ++i;
      } while (survivorSpaceBean != null && survivorSpaceBean.getUsage().getUsed() > 0 && i < 100);
    }
  }

  private static class DisabledMemoryAllocationMonitor extends MemoryAllocationMonitor {

    private final Set<String> missingCollectors;

    DisabledMemoryAllocationMonitor(Set<String> missingCollectors) {
      this.missingCollectors = missingCollectors;
    }

    @Override
    public void warnIfMonitoringDisabled() {
      System.out.println(missingCollectors.stream().collect(joining(", ", "[WARN] Could not collect ", " **")));
      System.out.println("  - Results may be less reliable");
      if (missingCollectors.contains("PS MarkSweep")) {
        System.out.println("  - Try rerunning with -XX:+ExplicitGCInvokesConcurrentAndUnloadsClasses");
      }
    }

    @Override
    public void reset() {
      // Do our best
      System.gc();
    }
  }
}
