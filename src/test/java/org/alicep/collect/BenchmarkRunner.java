package org.alicep.collect;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Ordering.usingToString;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.runner.Description.createTestDescription;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
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
  @Target(ElementType.FIELD)
  public @interface Configuration { }

  private final List<SingleBenchmark> benchmarks;

  private static List<SingleBenchmark> getBenchmarks(TestClass testClass) throws InitializationError {
    List<FrameworkMethod> methods = testClass.getAnnotatedMethods(Benchmark.class);
    Collection<?> configurations = getOnlyElement(testClass.getAnnotatedFieldValues(
        null, Configuration.class, Collection.class));
    Constructor<?> constructor = testClass.getOnlyConstructor();
    return methods.stream()
        .map(method -> new SingleBenchmark(testClass, constructor, method, configurations))
        .collect(toList());
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

  static class SingleBenchmark extends Runner implements Filterable, Sortable {
    private static int MIN_WARMUP_ITERATIONS = 6;
    private static int ITERATIONS = 10;

    private final FrameworkMethod method;
    private final List<Flavour> flavours;

    SingleBenchmark(
        TestClass testClass,
        Constructor<?> constructor,
        FrameworkMethod method,
        Collection<?> configurations) {
      this.method = method;
      flavours = configurations
          .stream()
          .map(config -> new Flavour(testClass, constructor, method.getName(), method, config))
          .collect(toList());
    }

    @Override
    public Description getDescription() {
      Description description = Description.createSuiteDescription(method.getName());
      flavours.forEach(flavour -> description.addChild(flavour.description));
      return description;
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
      flavours.removeIf(flavour -> !filter.shouldRun(flavour.description));
    }

    @Override
    public void sort(Sorter sorter) {
      flavours.sort((a, b) -> sorter.compare(a.description, b.description));
    }

    @Override
    public void run(RunNotifier notifier) {
      flavours.forEach(flavour -> flavour.initialize(notifier));
      warmup(notifier);
      measure(notifier);
      flavours.forEach(flavour -> flavour.complete(notifier));
    }

    private void warmup(RunNotifier notifier) {
      for (int i = 0; i < MIN_WARMUP_ITERATIONS; i++) {
        flavours.forEach(flavour -> flavour.warmup(notifier));
      }
    }

    private void measure(RunNotifier notifier) {
      ListMultimap<Flavour, Double> observations = ArrayListMultimap.create();
      for (int i = 0; i < ITERATIONS; ++i) {
        flavours.forEach(flavour -> observations.put(flavour, flavour.measure(notifier)));
      }
      summarize(observations);
    }

    private String title() {
      Benchmark benchmark = method.getAnnotation(Benchmark.class);
      if (benchmark.value().isEmpty()) {
        return method.getName();
      }
      return benchmark.value();
    }

    private void summarize(ListMultimap<Flavour, Double> observationsByFlavour) {
      String title = title();
      System.out.println(title);
      System.out.println(Stream.generate(() -> "-").limit(title.length()).collect(joining()));
      flavours.stream().sorted(usingToString().onResultOf(Flavour::config)).forEach(flavour -> {
        List<Double> observations = observationsByFlavour.get(flavour);
        if (!observations.stream().anyMatch(o -> o.isNaN())) {
          String best = formatNanos(Ordering.natural().min(observations));
          String worst = formatNanos(Ordering.natural().max(observations));
          double total = observations.stream().reduce(0.0, (a, b) -> a + b);
          String mean = formatNanos(total / observations.size());
          System.out.println(flavour.config + ": " + mean + " (" + best + "-" + worst + ")");
        }
      });
      System.out.println();
    }
  }

  private static class Flavour {

    private static Duration MIN_MEASUREMENT_TIME = Duration.ofMillis(100);

    private final Constructor<?> constructor;
    private final FrameworkMethod method;
    private final Object config;
    private final Description description;

    private Object test;
    private long attempts = 1;

    private static String name(String benchmarkName, Object config) {
      return benchmarkName + " [" + config + "]";
    }

    Flavour(
        TestClass testClass,
        Constructor<?> constructor,
        String benchmarkName,
        FrameworkMethod method,
        Object config) {
      this.constructor = constructor;
      this.method = method;
      this.config = config;
      description = createTestDescription(testClass.getJavaClass(), name(benchmarkName, config));
    }

    public Object config() {
      return config;
    }

    public void initialize(RunNotifier notifier) {
      notifier.fireTestStarted(description);
      try {
        test = constructor.newInstance(config);
      } catch (Exception e) {
        notifier.fireTestFailure(new Failure(description, e));
      }
    }

    public void warmup(RunNotifier notifier) {
      try {
        while (true) {
          long elapsed = timeAttempts(notifier);
          if (elapsed > MIN_MEASUREMENT_TIME.toNanos()) {
            return;
          }
          attempts *= 2;
        }
      } catch (Throwable t) {
        test = null;
        notifier.fireTestFailure(new Failure(description, t));
      }
    }

    public double measure(RunNotifier notifier) {
      if (test == null) return Double.NaN;
      try {
        while (true) {
          long elapsed = timeAttempts(notifier);
          if (test == null) {
            return Double.NaN;
          }
          if (elapsed > MIN_MEASUREMENT_TIME.toNanos()) {
            return (double) elapsed / attempts;
          }
          attempts *= 2;
        }
      } catch (Throwable t) {
        test = null;
        notifier.fireTestFailure(new Failure(description, t));
        return Double.NaN;
      }
    }

    public void complete(RunNotifier notifier) {
      if (test == null) return;
      notifier.fireTestFinished(description);
    }

    private long timeAttempts(RunNotifier notifier) throws Throwable {
      long startTime = System.nanoTime();
      for (long i = 0; i < attempts; ++i) {
        method.invokeExplosively(test);
      }
      long stopTime = System.nanoTime();
      return stopTime - startTime;
    }
  }

  private static final Map<Integer, String> SCALES = ImmutableMap.<Integer, String>builder()
      .put(0, "s")
      .put(-3, "ms")
      .put(-6, "μs")
      .put(-9, "ns")
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
