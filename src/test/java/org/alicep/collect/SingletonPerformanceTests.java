package org.alicep.collect;

import static java.util.Arrays.setAll;
import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
public class SingletonPerformanceTests<T> {

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
      new Config<>(BucketPairSpikeArraySet::new, longs),
      new Config<>(HashSet::new, strings),
      new Config<>(LinkedHashSet::new, strings),
      new Config<>(BucketPairSpikeArraySet::new, strings));

  private final Supplier<Set<T>> setFactory;
  private Set<T> singleton;

  private final T item;
  @SuppressWarnings("unchecked")
  private final T[] hitItems = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final T[] missItems = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final Set<T>[] singletons = (Set<T>[]) new Set<?>[30];

  int i = 0;

  public SingletonPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    item = config.itemFactory.createItem(0);
    singleton = setFactory.get();
    singleton.add(item);
    assertForked(singleton);
    setAll(hitItems, $ -> config.itemFactory.createItem(0));
    setAll(missItems, i -> config.itemFactory.createItem(i + 1));
    setAll(singletons, $ -> {
      Set<T> set = setFactory.get();
      set.add(item);
      return set;
    });
  }

  @Benchmark("Create a singleton set")
  public void create() {
    singleton = setFactory.get();
    singleton.add(item);
  }

  @Benchmark("forEach on a singleton set")
  @InterferenceWarning("This test sometimes JITs badly and consumes memory")
  public void forEach() {
    next(singletons).forEach(e -> assertNotNull(e));
  }

  @Benchmark("iterate through a singleton set")
  public void iterate() {
    Iterator<T> iterator = next(singletons).iterator();
    while (iterator.hasNext()) {
      assertNotNull(iterator.next());
    }
  }

  @Benchmark("Hit in a singleton set, identical")
  public void identityHit() {
    assertTrue(next(singletons).contains(item));
  }

  @Benchmark("Hit in a singleton set, not identical")
  public void hit() {
    assertTrue(singleton.contains(next(hitItems)));
  }

  @Benchmark("Miss in a singleton set")
  public void miss() {
    assertFalse(singleton.contains(next(missItems)));
  }

  private static void assertForked(Object object) {
    String className = object.getClass().getName();
    assertTrue(className + " not forked", !className.startsWith("java."));
  }

  private <V> V next(V[] array) {
    ++i;
    if (i == array.length) {
      i = 0;
    }
    return array[i];
  }
}
