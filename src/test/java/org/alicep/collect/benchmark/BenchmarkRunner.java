package org.alicep.collect.benchmark;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.runner.Description.createTestDescription;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

public class BenchmarkRunner extends ParentRunner<BenchmarkRunner.SingleBenchmark> {

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Benchmark {
    String value() default "";
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface InterferenceWarning { }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface Configuration { }

  private final List<SingleBenchmark> benchmarks;

  private static List<SingleBenchmark> getBenchmarks(TestClass testClass) throws InitializationError {
    try {
      List<FrameworkMethod> methods = testClass.getAnnotatedMethods(Benchmark.class);
      FrameworkField configurationsField = getOnlyElement(testClass.getAnnotatedFields(Configuration.class));
      List<?> configurations = (List<?>) configurationsField.get(null);
      testClass.getOnlyConstructor();
      List<SingleBenchmark> benchmarks = new ArrayList<>();
      for (FrameworkMethod method : methods) {
        benchmarks.add(new SingleBenchmark(
            testClass, method, configurationsField, configurations));
      }
      return benchmarks;
    } catch (RuntimeException | IllegalAccessException e) {
      throw new InitializationError(e);
    }
  }

  public BenchmarkRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
    benchmarks = getBenchmarks(getTestClass());
  }

  @Override
  protected List<SingleBenchmark> getChildren() {
    return benchmarks;
  }

  @Override
  protected Description describeChild(SingleBenchmark benchmark) {
    return benchmark.getDescription();
  }

  @Override
  public void run(RunNotifier notifier) {
    // Ensure the management monitors don't set themselves off mid-test if they've never run before
    ManagementMonitor monitor = new ManagementMonitor();
    monitor.stop();
    monitor.printIfChanged(new PrintStream(new ByteArrayOutputStream()));

    super.run(notifier);
  }

  @Override
  protected void runChild(SingleBenchmark benchmark, RunNotifier notifier) {
    benchmark.run(notifier);
  }

  static class SingleBenchmark extends ParentRunner<Flavour> {
    private final FrameworkMethod method;
    private final MemoryAllocationMonitor memoryAllocationMonitor;
    private final List<Flavour> flavours;

    SingleBenchmark(
        TestClass testClass,
        FrameworkMethod method,
        FrameworkField configurationsField,
        List<?> configurations) throws InitializationError {
      super(testClass.getJavaClass());
      this.method = method;
      this.memoryAllocationMonitor = MemoryAllocationMonitor.get();
      flavours = IntStream.iterate(0, i -> ++i)
          .limit(configurations.size())
          .mapToObj(index -> new Flavour(
              testClass,
              memoryAllocationMonitor,
              method.getName(),
              method,
              configurationsField,
              configurations.get(index),
              index))
          .sorted(Ordering.natural().onResultOf(flavour -> flavour.config().toString()))
          .collect(toList());
    }

    @Override
    public void run(RunNotifier notifier) {
      String title = title();
      System.out.println(title);
      System.out.println(Stream.generate(() -> "-").limit(title.length()).collect(joining()));
      if (method.getAnnotation(InterferenceWarning.class) != null
          && getDescription().getChildren().size() > 1) {
        System.out.println(" ** This test tends to be unreliable **");
        System.out.println("    Run in isolation for trustworthy results");
      }
      memoryAllocationMonitor.warnIfMonitoringDisabled();
      super.run(notifier);
      System.out.println();
    }

    @Override
    protected void runChild(Flavour flavour, RunNotifier notifier) {
      flavour.run(notifier);
    }

    @Override
    protected Description describeChild(Flavour child) {
      return child.getDescription();
    }

    @Override
    protected List<Flavour> getChildren() {
      return flavours;
    }

    private String title() {
      Benchmark benchmark = method.getAnnotation(Benchmark.class);
      if (!benchmark.value().isEmpty()) {
        return benchmark.value();
      } else {
        return method.getName();
      }
    }
  }

  private static class Flavour extends Runner {

    private static final double CONFIDENCE_INTERVAL_99_PERCENT = 2.58;
    private static Duration MIN_HOT_LOOP_TIME = Duration.ofMillis(10);
    private static int MIN_WARMUP_ITERATIONS = 5;
    private static Duration MIN_WARMUP_TIME = Duration.ofSeconds(1);
    private static Duration MAX_WARMUP_TIME = Duration.ofSeconds(10);
    private static int MIN_MEASUREMENT_ITERATIONS = 5;
    private static Duration MIN_MEASUREMENT_TIME = Duration.ofSeconds(1);

    private final Description description;
    private final Supplier<LongConsumer> hotLoopFactory;

    private final Object configuration;
    private final MemoryAllocationMonitor memoryAllocationMonitor;

    private static String name(String benchmarkName, Object config) {
      return benchmarkName + " [" + config + "]";
    }

    Flavour(
        TestClass testClass,
        MemoryAllocationMonitor memoryAllocationMonitor,
        String benchmarkName,
        FrameworkMethod method,
        FrameworkField configurationsField,
        Object configuration,
        int index) {
      this.configuration = configuration;
      this.memoryAllocationMonitor = memoryAllocationMonitor;
      description = createTestDescription(testClass.getJavaClass(), name(benchmarkName, configuration));
      hotLoopFactory = () -> BenchmarkCompiler.compileBenchmark(
          testClass.getJavaClass(),
          method.getMethod(),
          configurationsField.getField(),
          index,
          BenchmarkRunner::isCoreCollection);
    }

    @Override
    public Description getDescription() {
      return description;
    }

    /**
     * Run in a single method to ensure the JIT targets the generated hot loop code only
     */
    @Override
    public void run(RunNotifier notifier) {
      notifier.fireTestStarted(description);

      try {
        System.out.print(config() + ": ");
        System.out.flush();

        // Start in warmup phase, then move to timing once things have stabilised enough to trust the data.
        boolean timing = false;

        // Number of times to run the hot loop for
        long hotLoopIterations = 1;
        // Elapsed time (total time / iterations) for each timed iteration
        double[] elapsedTime = new double[(int) (MIN_MEASUREMENT_TIME.toNanos() / MIN_HOT_LOOP_TIME.toNanos()) + 1];

        // The time we started warming up
        long warmupStartTime = System.nanoTime();

        // The time we last saw JIT activity
        long noJitStartTime = Long.MAX_VALUE;

        // The time we started timing
        long timingStartTime = Long.MAX_VALUE;

        // The time the last hot loop finished
        long endTime;

        // Memory usage across all timed iterations
        long usageBeforeRun = 0;
        long usageAfterRun;

        // Monitor the JVM for suspicious activity
        ManagementMonitor monitor = new ManagementMonitor();

        // The hot loop we are timing
        LongConsumer hotLoop = hotLoopFactory.get();

        // How many iterations since the last restart
        int iterations = 0;

        do {
          if (iterations == 0) {
            if (timing) {
              memoryAllocationMonitor.prepareForBenchmark();
              usageBeforeRun = memoryAllocationMonitor.memoryUsed();
            }
            monitor.start();
          }

          long startTime = System.nanoTime();
          if (iterations == 0) {
            if (timing) {
              timingStartTime = startTime;
            } else {
              noJitStartTime = startTime;
            }
          }
          hotLoop.accept(hotLoopIterations);
          endTime = System.nanoTime();
          long elapsed = endTime - startTime;

          if (elapsed < MIN_HOT_LOOP_TIME.toNanos()) {
            // Restart if the hot loop did not take enough time running
            hotLoopIterations *= 2;
            iterations = -1;
          } else if (timing) {
            // Record elapsed time if we're in the timing loop
            elapsedTime[iterations] = (double) elapsed / hotLoopIterations;

            // Break out of the loop if we've run enough iterations over enough time
            boolean runMinIterations = iterations >= MIN_MEASUREMENT_ITERATIONS;
            boolean runMinTime = (endTime - timingStartTime) > MIN_MEASUREMENT_TIME.toNanos();
            if (runMinIterations && runMinTime) {
              break;
            }
          } else {
            boolean runMaxTime = (endTime - warmupStartTime) > MAX_WARMUP_TIME.toNanos();

            // Restart if we saw the JIT trigger, and we're within the maximum warmup time
            if (!runMaxTime && monitor.jitMetricChanged()) {
              iterations = -1;
            }

            // Start timing if we've run enough iterations over enough time since the last JIT.
            boolean runMinIterations = iterations >= MIN_WARMUP_ITERATIONS;
            boolean runMinTime = (endTime - noJitStartTime) > MIN_WARMUP_TIME.toNanos();
            if (runMinIterations && runMinTime) {
              timing = true;
              iterations = -1;
            }
          }

          iterations++;
        } while (true);

        monitor.stop();
        usageAfterRun = memoryAllocationMonitor.memoryUsed();
        double usagePerLoop = (double) (usageAfterRun - usageBeforeRun) / iterations / hotLoopIterations;
        hotLoop = null;
        summarize(elapsedTime, iterations, usagePerLoop, monitor);
        notifier.fireTestFinished(description);
      } catch (Throwable t) {
        System.out.print(t.getClass().getSimpleName());
        if (t.getMessage() != null) {
          System.out.print(": ");
          System.out.print(t.getMessage());
        }
        System.out.println();
        notifier.fireTestFailure(new Failure(description, t));
      }
    }

    private static void summarize(double[] elapsedTime, int iterations, double memoryUsage, ManagementMonitor monitor) {
      String timeSummary = summarizeTime(elapsedTime, iterations);
      String memorySummary = ManagementMonitor.formatBytes((long) memoryUsage);
      System.out.println(timeSummary + " " + memorySummary);
      monitor.printIfChanged(System.out);
    }

    private static String summarizeTime(double[] elapsedTime, int iterations) {
      double total = Arrays.stream(elapsedTime).limit(iterations).sum();
      double mean = total / iterations;
      double totalVariance = Arrays.stream(elapsedTime).limit(iterations).map(a -> a*a - mean*mean).sum();
      double sampleError = Math.sqrt(totalVariance / (iterations - 1)) * CONFIDENCE_INTERVAL_99_PERCENT;
      String timeSummary = formatNanos(mean) + " (±" + formatNanos(sampleError) + ")";
      return timeSummary;
    }

    public Object config() {
      return configuration;
    }
  }

  private static boolean isCoreCollection(Class<?> cls) {
    return cls.getPackage().getName().equals("java.util")
        && !cls.isInterface()
        && (Map.class.isAssignableFrom(cls) || Set.class.isAssignableFrom(cls));
  }

  private static final Map<Integer, String> SCALES = ImmutableMap.<Integer, String>builder()
      .put(0, "s")
      .put(-3, "ms")
      .put(-6, "μs")
      .put(-9, "ns")
      .put(-12, "ps")
      .build();

  static String formatNanos(double nanos) {
    checkArgument(nanos >= 0);
    if (nanos == 0) return "0s";
    double timePerAttempt = nanos;
    int scale = -9; // nanos
    while (timePerAttempt < 1.0) {
      timePerAttempt *= 1000;
      scale -= 3;
    }
    while (timePerAttempt >= 999 && scale < 0) {
      timePerAttempt /= 1000;
      scale += 3;
    }
    String significand;
    if (timePerAttempt < 9.995) {
      significand = String.format("%.2f", timePerAttempt);
    } else if (timePerAttempt < 99.95) {
      significand = String.format("%.1f", timePerAttempt);
    } else {
      significand = Long.toString(Math.round(timePerAttempt));
    }

    if (SCALES.containsKey(scale)) {
      return String.format("%s %s", significand, SCALES.get(scale));
    } else {
      return String.format("%se%d s", significand, scale);
    }
  }
}
