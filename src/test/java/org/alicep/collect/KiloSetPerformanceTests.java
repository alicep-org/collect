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

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.alicep.benchmark.BenchmarkRunner.InterferenceWarning;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;

@RunWith(BenchmarkRunner.class)
public class KiloSetPerformanceTests<T> {

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
  private Set<T> kiloSet;

  @SuppressWarnings("unchecked")
  private final T[] items = (T[]) new Object[1000];
  @SuppressWarnings("unchecked")
  private final T[] hitItems = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final T[] missItems = (T[]) new Object[5000];

  int i = 0;

  public KiloSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    setAll(items, config.itemFactory::createItem);
    setAll(hitItems, i -> config.itemFactory.createItem(i % items.length));
    setAll(missItems, i -> config.itemFactory.createItem(i + items.length));
    kiloSet = setFactory.get();
    for (T item : items) {
      kiloSet.add(item);
    }
  }

  @Benchmark("Create a 1K-element set")
  public void create() {
    kiloSet = setFactory.get();
    for (T item : items) {
      kiloSet.add(item);
    }
  }

  @Benchmark("Iterate through a 1K-element set")
  @InterferenceWarning("This test sometimes JITs badly and consumes memory")
  public void iterate() {
    kiloSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 1K-element set, identical")
  public void identityHit() {
    assertTrue(kiloSet.contains(next(items)));
  }

  @Benchmark("Hit in a 1K-element set, not identical")
  public void hit() {
    assertTrue(kiloSet.contains(next(hitItems)));
  }

  @Benchmark("Miss in a 1K-element set")
  public void miss() {
    assertFalse(kiloSet.contains(next(missItems)));
  }

  private <V> V next(V[] array) {
    ++i;
    if (i == array.length) {
      i = 0;
    }
    return array[i];
  }
}
