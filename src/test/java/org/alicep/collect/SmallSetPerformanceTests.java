package org.alicep.collect;

import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.alicep.collect.benchmark.BenchmarkRunner;
import org.alicep.collect.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.collect.benchmark.BenchmarkRunner.Configuration;
import org.alicep.collect.benchmark.BenchmarkRunner.InterferenceWarning;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;

@RunWith(BenchmarkRunner.class)
public class SmallSetPerformanceTests<T> {

  static class Config<T> {
    private final Supplier<Set<T>> setFactory;
    private final ItemFactory<T> itemFactory;

    public Config(
        Supplier<Set<T>> setFactory,
        ItemFactory<T> itemFactory) {
      this.setFactory = setFactory;
      this.itemFactory = itemFactory;
    }

    @Override
    public String toString() {
      String setType = setFactory.get().getClass().getSimpleName();
      String itemType = itemFactory.createItem(0).getClass().getSimpleName();
      return setType + "<" + itemType + ">";
    }
  }

  @Configuration
  public static final ImmutableList<Config<?>> CONFIGURATIONS = ImmutableList.of(
      new Config<>(HashSet::new, longs),
      new Config<>(LinkedHashSet::new, longs),
      new Config<>(ArraySet::new, longs),
      new Config<>(HashSet::new, strings),
      new Config<>(LinkedHashSet::new, strings),
      new Config<>(ArraySet::new, strings));

  private final Supplier<Set<T>> setFactory;
  private final Set<T> littleSet;

  @SuppressWarnings("unchecked")
  private final T[] items = (T[]) new Object[5000];

  int i = 0;

  public SmallSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    for (int i = 0; i < items.length; ++i) {
      items[i] = config.itemFactory.createItem(i);
    }
    littleSet = setFactory.get();
    for (int i = 0; i < 6; i++) {
      littleSet.add(items[i]);
    }
    ArraySet.hits = 0;
    ArraySet.unnecessaryEqualityChecks = 0;
    ArraySet.probes = 0;
    ArraySet.skippedEqualityChecks = 0;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("hits: " + ArraySet.hits);
      System.out.println("unnecessary equality checks: " + ArraySet.unnecessaryEqualityChecks);
      System.out.println("spillover bucket lookups: " + ArraySet.probes);
      System.out.println("skipped equality checks: " + ArraySet.skippedEqualityChecks);
    }));
  }

  @Benchmark("Create a 6-element set")
  public void create() {
    Set<T> set = setFactory.get();
    for (int i = 0; i < 6; ++i) {
      set.add(items[i]);
    }
  }

  @Benchmark("Iterate through a 6-element set")
  @InterferenceWarning  // Hitting java.lang.Iterable.forEach, which cannot be cloned
  public void iterate() {
    littleSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 6-element set")
  public void hit() {
    assertTrue(littleSet.contains(items[i]));
    if (++i == 6) i = 0;
  }

  @Benchmark("Miss in a 6-element set")
  public void miss() {
    assertFalse(littleSet.contains(items[i + 6]));
    if (++i + 6 == items.length) {
      i = 0;
    }
  }
}
