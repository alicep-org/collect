package org.alicep.collect;

import static java.util.Arrays.setAll;
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
public class MegaSetPerformanceTests<T> {

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
      String mapType = setFactory.get().getClass().getSimpleName();
      String itemType = itemFactory.createItem(0).getClass().getSimpleName();
      return mapType + "<" + itemType + ">";
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
  private final ItemFactory<T> itemFactory;
  private Set<T> bigSet;

  @SuppressWarnings("unchecked")
  private final T[] elements = (T[]) new Object[1_000_000];

  int i = 0;

  public MegaSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;
    itemFactory = config.itemFactory;

    setAll(elements, itemFactory::createItem);
    bigSet = setFactory.get();
    for (T item : elements) {
      bigSet.add(item);
    }
  }

  @Benchmark("Create a 1M-element map")
  public void create() {
    bigSet = setFactory.get();
    for (T item : elements) {
      bigSet.add(item);
    }
  }

  @Benchmark("Iterate through a 1M-element map")
  @InterferenceWarning
  public void iterate() {
    bigSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 1M-element map")
  public void hit() {
    assertTrue(bigSet.contains(elements[i]));
    if (++i == elements.length) i = 0;
  }

  @Benchmark("Miss in a 1M-element map")
  public void miss() {
    assertFalse(bigSet.contains(itemFactory.createItem(i++ + elements.length)));
  }
}
