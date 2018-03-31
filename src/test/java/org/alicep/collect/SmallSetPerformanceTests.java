package org.alicep.collect;

import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntFunction;
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
  private Set<T> littleSet;

  @SuppressWarnings("unchecked")
  private final T[] items = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final T[] hitItems = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final T[] missItems = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final Set<T>[] littleSets = (Set<T>[]) new Set<?>[50];

  int i = 0;

  public SmallSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    fill(items, config.itemFactory::createItem);
    fill(hitItems, i -> config.itemFactory.createItem(i % items.length));
    fill(missItems, i -> config.itemFactory.createItem(i + items.length));

    littleSet = setFactory.get();
    for (T item : items) {
      littleSet.add(item);
    }
    fill(littleSets, $ -> {
      Set<T> set = setFactory.get();
      for (T item : items) {
        set.add(item);
      }
      return set;
    });
  }

  @Benchmark("Create a 6-element set")
  public void create() {
    littleSet = setFactory.get();
    for (int i = 0; i < 6; ++i) {
      littleSet.add(items[i]);
    }
  }

  @Benchmark("forEach on a 6-element set")
  @InterferenceWarning("This test sometimes JITs badly and consumes memory")
  public void forEach() {
    next(littleSets).forEach(e -> assertNotNull(e));
  }

  @Benchmark("iterate through a 6-element set")
  public void iterate() {
    Iterator<T> iterator = next(littleSets).iterator();
    while (iterator.hasNext()) {
      assertNotNull(iterator.next());
    }
  }

  @Benchmark("Hit in a 6-element set, identical")
  public void identityHit() {
    assertTrue(littleSet.contains(next(items)));
  }

  @Benchmark("Hit in a 6-element set, not identical")
  public void hit() {
    assertTrue(littleSet.contains(next(hitItems)));
  }

  @Benchmark("Miss in a 6-element set")
  public void miss() {
    assertFalse(littleSet.contains(next(missItems)));
  }

  private <V> V next(V[] array) {
    ++i;
    if (i == array.length) {
      i = 0;
    }
    return array[i];
  }

  private static <T> void fill(T[] array, IntFunction<T> factory) {
    for (int i = 0; i < array.length; ++i) {
      array[i] = factory.apply(i);
    }
  }
}
