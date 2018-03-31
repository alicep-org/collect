package org.alicep.collect;

import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

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
public class EmptySetPerformanceTests<T> {

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
  private final Set<T> emptySet;

  @SuppressWarnings("unchecked")
  private final T[] items = (T[]) new Object[5000];

  int i = 0;

  public EmptySetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    for (int i = 0; i < items.length; ++i) {
      items[i] = config.itemFactory.createItem(i);
    }
    emptySet = setFactory.get();
  }

  @Benchmark("Create an empty set")
  public void create() {
    setFactory.get();
  }

  @Benchmark("Iterate through an empty set")
  @InterferenceWarning  // Hitting java.lang.Iterable.forEach, which cannot be cloned
  public void iterate() {
    emptySet.forEach(e -> fail("Found " + e));
  }

  @Benchmark("Miss in an empty set")
  public void miss() {
    assertFalse(emptySet.contains(items[i]));
    if (++i == items.length) {
      i = 0;
    }
  }
}
