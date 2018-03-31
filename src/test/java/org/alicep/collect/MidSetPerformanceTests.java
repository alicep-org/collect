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
public class MidSetPerformanceTests<T> {

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
  private final T[] items = (T[]) new Object[4900];

  int i = 0;

  public MidSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    for (int i = 0; i < items.length; ++i) {
      items[i] = config.itemFactory.createItem(i);
    }
    littleSet = setFactory.get();
    for (int i = 0; i < 49; i++) {
      littleSet.add(items[i]);
    }
  }

  @Benchmark("Create a 49-element set")
  public void create() {
    // 49 elements is right before an ArraySet resize
    // 50 elements would cost significantly more due to paying down the amortisation costs every time
    Set<T> set = setFactory.get();
    for (int i = 0; i < 49; ++i) {
      set.add(items[i]);
    }
  }

  @Benchmark("Iterate through a 49-element set")
  @InterferenceWarning  // Hitting java.lang.Iterable.forEach, which cannot be cloned
  public void iterate() {
    littleSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 49-element set")
  public void hit() {
    assertTrue(littleSet.contains(items[i]));
    if (++i == 49) i = 0;
  }

  @Benchmark("Miss in a 49-element set")
  public void miss() {
    // 49 elements is right before an ArraySet resize
    // 50 elements would be a lot faster due to lower occupancy
    assertFalse(littleSet.contains(items[i + 49]));
    if (++i + 49 == items.length) {
      i = 0;
    }
  }
}
