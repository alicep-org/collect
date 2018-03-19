package org.alicep.collect.benchmark;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.runner.Description.createTestDescription;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  protected void runChild(SingleBenchmark benchmark, RunNotifier notifier) {
    benchmark.run(notifier);
  }

  static class SingleBenchmark extends ParentRunner<Flavour> {
    private final FrameworkMethod method;
    private final List<Flavour> flavours;

    SingleBenchmark(
        TestClass testClass,
        FrameworkMethod method,
        FrameworkField configurationsField,
        List<?> configurations) throws InitializationError {
      super(testClass.getJavaClass());
      this.method = method;
      flavours = IntStream.iterate(0, i -> ++i)
          .limit(configurations.size())
          .mapToObj(index -> new Flavour(
              testClass,
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

    private static int WARMUP_ITERATIONS = 6;
    private static int ITERATIONS = 10;
    private static Duration MIN_MEASUREMENT_TIME = Duration.ofMillis(100);

    private final Description description;
    private final Supplier<LongConsumer> hotLoopFactory;

    private final Object configuration;
    private long attempts = 1;

    private static String name(String benchmarkName, Object config) {
      return benchmarkName + " [" + config + "]";
    }

    Flavour(
        TestClass testClass,
        String benchmarkName,
        FrameworkMethod method,
        FrameworkField configurationsField,
        Object configuration,
        int index) {
      this.configuration = configuration;
      description = createTestDescription(testClass.getJavaClass(), name(benchmarkName, configuration));
      hotLoopFactory = () -> BenchmarkCompiler.compileBenchmark(
          testClass.getJavaClass(), method.getMethod(), configurationsField.getField(), index);
    }

    @Override
    public Description getDescription() {
      return description;
    }

    @Override
    public void run(RunNotifier notifier) {
      System.out.print(config() + ": ");
      System.out.flush();
      try {
        List<Double> observations = new ArrayList<>();
        notifier.fireTestStarted(description);
        LongConsumer hotLoop = hotLoopFactory.get();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
          measure(hotLoop);
        }
        for (int i = 0; i < ITERATIONS; ++i) {
          observations.add(measure(hotLoop));
        }
        hotLoop = null;
        if (!observations.stream().anyMatch(o -> o.isNaN())) {
          String best = formatNanos(Ordering.natural().min(observations));
          String worst = formatNanos(Ordering.natural().max(observations));
          double total = observations.stream().reduce(0.0, (a, b) -> a + b);
          String mean = formatNanos(total / observations.size());
          System.out.println(mean + " (" + best + "-" + worst + ")");
        }
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

    public Object config() {
      return configuration;
    }

    private double measure(LongConsumer hotLoop) {
      while (true) {
        long startTime = System.nanoTime();
        hotLoop.accept(attempts);
        long elapsed = System.nanoTime() - startTime;
        if (elapsed > MIN_MEASUREMENT_TIME.toNanos()) {
          return (double) elapsed / attempts;
        }
        attempts *= 2;
      }
    }
  }

  private static final Map<Integer, String> SCALES = ImmutableMap.<Integer, String>builder()
      .put(0, "s")
      .put(-3, "ms")
      .put(-6, "μs")
      .put(-9, "ns")
      .put(-12, "ps")
      .build();

  private static String formatNanos(double nanos) {
    checkArgument(nanos > 0);
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
