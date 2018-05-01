package org.alicep.collect;

import static java.util.Arrays.setAll;
import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.alicep.benchmark.BenchmarkRunner.InterferenceWarning;
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
  private Set<T> emptySet;

  @SuppressWarnings("unchecked")
  private final T[] items = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final Set<T>[] emptySets = (Set<T>[]) new Set<?>[50];

  int i = 0;

  public EmptySetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    emptySet = setFactory.get();
    setAll(items, config.itemFactory::createItem);
    setAll(emptySets, $ -> setFactory.get());
  }

  @Benchmark("Create an empty set")
  public void create() {
    emptySet = setFactory.get();
  }

  @Benchmark("forEach on an empty set")
  @InterferenceWarning("This test sometimes JITs badly and consumes memory")
  public void forEach() {
    next(emptySets).forEach(e -> fail("Found " + e));
  }

  @Benchmark("iterate through an empty set")
  public void iterate() {
    Iterator<T> iterator = next(emptySets).iterator();
    while (iterator.hasNext()) {
      assertNotNull(iterator.next());
    }
  }

  @Benchmark("Miss in an empty set")
  public void miss() {
    assertFalse(emptySet.contains(next(items)));
  }

  private <V> V next(V[] array) {
    ++i;
    if (i == array.length) {
      i = 0;
    }
    return array[i];
  }
}
