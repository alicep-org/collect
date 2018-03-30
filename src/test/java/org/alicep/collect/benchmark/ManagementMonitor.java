package org.alicep.collect.benchmark;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.management.ManagementFactory.getClassLoadingMXBean;
import static java.lang.management.ManagementFactory.getCompilationMXBean;
import static java.lang.management.ManagementFactory.getGarbageCollectorMXBeans;
import static org.alicep.collect.benchmark.BenchmarkRunner.formatNanos;

import java.io.PrintStream;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

class ManagementMonitor {

  private interface Monitor {
    void start();
    void stop();
    void printIfChanged(PrintStream ps);
  }

  private static class GCMonitor implements Monitor {
    private final GarbageCollectorMXBean bean;
    private long startCount;
    private long startTime;
    private long stopCount;
    private long stopTime;

    public GCMonitor(GarbageCollectorMXBean bean) {
      this.bean = bean;
    }

    @Override
    public void start() {
      startCount = bean.getCollectionCount();
      startTime = bean.getCollectionTime();
    }

    @Override
    public void stop() {
      stopCount = bean.getCollectionCount();
      stopTime = bean.getCollectionTime();
    }

    public boolean memoryPressureSeen() {
      return bean.getCollectionCount() > startCount;
    }

    @Override
    public void printIfChanged(PrintStream ps) {
      long sweeps = stopCount - startCount;
      if (sweeps > 0) {
        String time = formatNanos(stopTime - startTime);
        ps.println("  * " + sweeps + " " + bean.getName() + " collections over " + time);
      }
    }
  }

  private static class CompilerMonitor implements Monitor {
    private final CompilationMXBean bean;
    private long startTime;
    private long stopTime;

    public CompilerMonitor(CompilationMXBean bean) {
      this.bean = bean;
    }

    @Override
    public void start() {
      startTime = bean.getTotalCompilationTime();
    }

    @Override
    public void stop() {
      stopTime = bean.getTotalCompilationTime();
    }

    public boolean changed() {
      return bean.getTotalCompilationTime() != startTime;
    }

    @Override
    public void printIfChanged(PrintStream ps) {
      long time = stopTime - startTime;
      if (time > 0) {
        ps.println("  * " + formatNanos(time * 1000) + " compiling");
      }
    }
  }

  private static class ClassLoaderMonitor implements Monitor {

    private final ClassLoadingMXBean bean;
    private long startLoaded;
    private long startUnloaded;
    private long stopLoaded;
    private long stopUnloaded;

    public ClassLoaderMonitor(ClassLoadingMXBean bean) {
      this.bean = bean;
    }

    @Override
    public void start() {
      startLoaded = bean.getTotalLoadedClassCount();
      startUnloaded = bean.getUnloadedClassCount();
    }

    @Override
    public void stop() {
      stopLoaded = bean.getTotalLoadedClassCount();
      stopUnloaded = bean.getUnloadedClassCount();
    }

    @Override
    public void printIfChanged(PrintStream ps) {
      if (stopLoaded > startLoaded) {
        ps.println("  * " + (stopLoaded - startLoaded) + " classes loaded");
      }
      if (stopUnloaded > startUnloaded) {
        ps.println("  * " + (stopUnloaded - startUnloaded) + " classes unloaded");
      }
    }
  }

  private static class CodeCacheMonitor implements Monitor {

    private final MemoryPoolMXBean codeCacheBean;
    private long startSize;

    public CodeCacheMonitor(List<MemoryPoolMXBean> poolBeans) {
      this.codeCacheBean = poolBeans.stream()
          .filter(poolBean -> poolBean.getName().equals("Code Cache"))
          .findAny()
          .orElseThrow(() -> new AssertionError("Code Cache memory pool not found"));
    }

    @Override
    public void start() {
      startSize = codeCacheBean.getUsage().getUsed();
    }

    @Override
    public void stop() { }

    @Override
    public void printIfChanged(PrintStream ps) { }

    public boolean codeCacheIncreased() {
      return codeCacheBean.getUsage().getUsed() > startSize;
    }

  }

  private static final Map<Integer, String> SCALES = ImmutableMap.<Integer, String>builder()
      .put(0, "")
      .put(3, "K")
      .put(6, "M")
      .put(9, "G")
      .put(12, "T")
      .build();

  static String formatBytes(long bytes) {
    checkArgument(bytes >= 0);
    if (bytes < 995) return bytes + "B";
    double scaled = bytes;
    int scale = 0;
    while (scaled >= 995) {
      scaled /= 1000;
      scale += 3;
    }
    String significand;
    if (scaled < 9.95) {
      significand = String.format("%.1f", scaled);
    } else if (scaled < 99.5) {
      significand = String.format("%.1f", scaled);
    } else {
      significand = Long.toString(Math.round(scaled * 10) / 10);
    }

    if (SCALES.containsKey(scale)) {
      return String.format("%s%sB", significand, SCALES.get(scale));
    } else {
      return String.format("%se%dB", significand, scale);
    }

  }

  private final List<Monitor> monitors = new ArrayList<>();
  private final List<GCMonitor> gcMonitors = new ArrayList<>();
  private final CompilerMonitor compilerMonitor;
  private final CodeCacheMonitor codeCacheMonitor;

  public ManagementMonitor() {
    compilerMonitor = new CompilerMonitor(getCompilationMXBean());
    getGarbageCollectorMXBeans()
        .stream()
        .map(GCMonitor::new)
        .peek(gcMonitors::add)
        .forEach(monitors::add);
    monitors.add(new ClassLoaderMonitor(getClassLoadingMXBean()));
    codeCacheMonitor = new CodeCacheMonitor(ManagementFactory.getMemoryPoolMXBeans());
    monitors.add(codeCacheMonitor);
  }

  public void start() {
    monitors.forEach(Monitor::start);
  }

  public void stop() {
    monitors.forEach(Monitor::stop);
  }

  public boolean jitMetricChanged() {
    return compilerMonitor.changed() || codeCacheMonitor.codeCacheIncreased();
  }

  public void printIfChanged(PrintStream ps) {
    monitors.forEach(snapshot -> snapshot.printIfChanged(ps));
  }

  public boolean memoryPressureSeen() {
    return gcMonitors.stream().anyMatch(GCMonitor::memoryPressureSeen);
  }
}