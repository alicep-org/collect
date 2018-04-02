package org.alicep.collect.benchmark;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.lang.Math.sqrt;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.alicep.collect.benchmark.Bytes.bytes;
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
import java.util.function.LongUnaryOperator;
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
  public @interface InterferenceWarning {
    String value() default "This test tends to be unreliable";
  }

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
          .sorted(Ordering.natural().onResultOf(flavour -> {
            Object config = flavour.config();
            if (config instanceof Comparable) {
              return (Comparable<?>) config;
            } else {
              return config.toString();
            }
          }))
          .collect(toList());
    }

    @Override
    protected String getName() {
      return getTestClass().getName() + "#" + method.getName();
    }

    @Override
    public void run(RunNotifier notifier) {
      String title = title();
      System.out.println(title);
      System.out.println(Stream.generate(() -> "-").limit(title.length()).collect(joining()));
      InterferenceWarning interferenceWarning = method.getAnnotation(InterferenceWarning.class);
      if (interferenceWarning != null && getDescription().getChildren().size() > 1) {
        System.out.println(" ** " + interferenceWarning.value() + " **");
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

    private static Duration MIN_HOT_LOOP_TIME = Duration.ofMillis(50);
    private static int MIN_MEASUREMENT_ITERATIONS = 5;
    private static final double TARGET_ERROR = 0.01;

    private static final double OUTLIER_EWMAV_WEIGHT = 0.1;
    private static final int OUTLIER_WINDOW = 20;
    private static final double CONFIDENCE_INTERVAL_99_PERCENT = 2.58;

    private final Description description;
    private final Supplier<LongUnaryOperator> hotLoopFactory;

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

        // Number of times to run the hot loop for
        long hotLoopIterations = 1;

        // Elapsed time (total time / iterations) for each timed iteration
        double[] timings = new double[50];

        // Elapsed time statistic sources
        double tS = 0.0;
        double tSS = 0.0;
        double id = 0.0;
        double ewma = 0.0;
        double ewmas = 0.0;

        // Memory usage across all timed iterations
        long usageBeforeRun = 0;
        long usageAfterRun;

        // Monitor the JVM for suspicious activity
        ManagementMonitor monitor = new ManagementMonitor();

        // The hot loop we are timing
        LongUnaryOperator hotLoop = hotLoopFactory.get();

        // How many timing samples we've taken
        int timingSamples = 0;

        // How many memory samples we've taken
        int memorySamples = 0;

        do {
          if (memorySamples == 0) {
            memoryAllocationMonitor.prepareForBenchmark();
            usageBeforeRun = memoryAllocationMonitor.memoryUsed();
            monitor.start();
          }
          if (timingSamples == 0) {
            tS = 0.0;
            tSS = 0.0;
          }

          long elapsed = hotLoop.applyAsLong(hotLoopIterations);
          if (elapsed < MIN_HOT_LOOP_TIME.toNanos()) {
            // Restart if the hot loop did not take enough time running
            hotLoopIterations = hotLoopIterations + (hotLoopIterations >> 1) + 1;
            timingSamples = -1;
            memorySamples = -1;
          } else {
            // Record elapsed time if we're in the timing loop
            if (timings.length == timingSamples) {
              timings = Arrays.copyOf(timings, timings.length * 2);
            }
            double iterationTime = (double) elapsed / hotLoopIterations;

            if (timingSamples >= OUTLIER_WINDOW) {
              if (isOutlier(iterationTime, ewma / id, ewmas / id, timingSamples)) {
                continue;
              }
            }

            timings[timingSamples] = iterationTime;
            tS += iterationTime;
            tSS += iterationTime * iterationTime;
            id = updateEwmav(id, 1.0);
            ewma = updateEwmav(ewma, iterationTime);
            ewmas = updateEwmav(ewmas, iterationTime * iterationTime);

            // Remove old outliers
            // We do this as we run so that the sample error calculations do not include erroneous data
            if (timingSamples >= OUTLIER_WINDOW) {
              int firstIndex = timingSamples - OUTLIER_WINDOW;
              for (int index = timingSamples - OUTLIER_WINDOW; index >= firstIndex; index--) {
                if (isOutlier(timings, index, timingSamples - index)) {
                  double value = timings[index];
                  tS -= value;
                  tSS -= value * value;
                  for (int i = index + 1; i < timingSamples; ++i) {
                    timings[i - 1] = timings[i];
                  }
                  timings[timingSamples] = 0.0;
                  firstIndex = Math.max(index - OUTLIER_WINDOW, 0);
                  timingSamples--;
                }
              }
            }

            // Calculate ongoing sample error
            double sampleError = sqrt((tSS - tS*tS/(timingSamples + 1)) / ((timingSamples + 1) * timingSamples));
            double confidenceInterval = sampleError * CONFIDENCE_INTERVAL_99_PERCENT;
            boolean lowSampleError = confidenceInterval * timingSamples < tS * TARGET_ERROR;

            // Break out of the loop if we're confident our error is low
            boolean enoughSamples = timingSamples >= MIN_MEASUREMENT_ITERATIONS;
            if (enoughSamples && lowSampleError) {
              monitor.stop();
              usageAfterRun = memoryAllocationMonitor.memoryUsed();
              timingSamples++;
              break;
            }
          }

          timingSamples++;
          memorySamples++;
        } while (true);

        double memoryUsage = (double)
            (usageAfterRun - usageBeforeRun - memoryAllocationMonitor.approximateBaselineError()) / hotLoopIterations;
        summarize(tS, tSS, timingSamples, memoryUsage, memorySamples, monitor);
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

    private static boolean isOutlier(double[] timings, int index, int samples) {
      double id = 0.0;
      double ewma = 0.0;
      double ewmas = 0.0;
      for (int i = 0; i < samples; ++i) {
        double value = timings[index + i + 1];
        checkState(value > 0.0);
        id = updateEwmav(id, 1.0);
        ewma = updateEwmav(ewma, value);
        ewmas = updateEwmav(ewmas, value * value);
      }
      return isOutlier(timings[index], ewma / id, ewmas / id, samples);
    }

    /**
     * Discard samples more than 3 standard deviations above the mean of the subsequent readings.
     *
     * <p>About 99.7% of points lie within this range, so this should not be biasing results too
     * significantly downwards.
     */
    private static boolean isOutlier(double value, double ewma, double ewmas, int samples) {
      double ewmasd = sqrt((ewmas - ewma*ewma) * samples/(samples - 1));
      return value > ewma + ewmasd * 3;
    }

    private static double updateEwmav(double mav, double value) {
      return OUTLIER_EWMAV_WEIGHT * value + (1 - OUTLIER_EWMAV_WEIGHT) * mav;
    }

    private static void summarize(
        double tS,
        double tSS,
        int iterations,
        double memoryUsage,
        int memorySamples,
        ManagementMonitor monitor) {
      String timeSummary = summarizeTime(tS, tSS, iterations);
      String memorySummary = summarizeMemory(memoryUsage, memorySamples);
      System.out.println(timeSummary + ", " + memorySummary);
      monitor.printIfChanged(System.out);
    }

    private static String summarizeTime(double tS, double tSS, int iterations) {
      double total = tS;
      double mean = total / iterations;
      double sd = sqrt((tSS - tS*tS/iterations) / (iterations - 1));
      String timeSummary = formatNanos(mean) + " (±" + formatNanos(sd * CONFIDENCE_INTERVAL_99_PERCENT) + ")";
      return timeSummary;
    }

    private static String summarizeMemory(double memoryUsage, int memorySamples) {
      return bytes((long) memoryUsage/memorySamples).toString();
    }

    public Object config() {
      return configuration;
    }
  }

  private static boolean isCoreCollection(Class<?> cls) {
    boolean isInJavaUtil = cls.getPackage().getName().equals("java.util");
    boolean isClass = !cls.isInterface();
    boolean isCollection = Map.class.isAssignableFrom(cls) || Set.class.isAssignableFrom(cls);
    return isInJavaUtil && isClass && isCollection;
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
