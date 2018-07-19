package org.alicep.collect;

import static java.util.Arrays.setAll;
import static org.alicep.collect.ItemFactory.longs;
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
import com.koloboke.collect.impl.hash.LHashLongSetFactoryImpl;
import com.koloboke.collect.impl.hash.LHashObjSetFactoryImpl;

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
      new Config<>(new LHashLongSetFactoryImpl()::newUpdatableSet, longs),
      new Config<>(new LHashObjSetFactoryImpl<Long>()::newUpdatableSet, longs),
      new Config<>(HashSet::new, longs),
      new Config<>(LinkedHashSet::new, longs),
//      new Config<>(SlowCompactingArraySet::new, longs),
      new Config<>(Faster64IndexCompactionArraySet::new, longs),
//      new Config<>(NoScanDuringCompactionArraySet::new, longs),
//      new Config<>(NoScanDuringLargerCompactionArraySet::new, longs),
      new Config<>(BucketPairSpikeArraySet::new, longs)
//      new Config<>(HashSet::new, strings),
//      new Config<>(LinkedHashSet::new, strings),
//      new Config<>(ArraySet::new, strings)
      );

  private final Supplier<Set<T>> setFactory;
  private Set<T> midSet;

  @SuppressWarnings("unchecked")
  private final T[] items = (T[]) new Object[49];
  @SuppressWarnings("unchecked")
  private final T[] hitItems = (T[]) new Object[5000];
  @SuppressWarnings("unchecked")
  private final T[] missItems = (T[]) new Object[5000];

  int i = 0;

  public MidSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;

    setAll(items, config.itemFactory::createItem);
    setAll(hitItems, i -> config.itemFactory.createItem(i % items.length));
    setAll(missItems, i -> config.itemFactory.createItem(i + items.length));
    midSet = setFactory.get();
    for (int i = 0; i < 49; i++) {
      midSet.add(items[i]);
    }
  }

  @Benchmark("Create a 49-element set")
//  @MinBenchmarkTime(millis = 360_000_000)
  public void create() {
    // 49 elements is right before an ArraySet resize
    // 50 elements would cost significantly more due to paying down the amortisation costs every time
    midSet = setFactory.get();
    for (int i = 0; i < 49; ++i) {
      midSet.add(items[i]);
    }
  }

  @Benchmark("Iterate through a 49-element set")
  @InterferenceWarning("This test sometimes JITs badly and consumes memory")
  public void iterate() {
    midSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 49-element set, identical")
  public void identityHit() {
    assertTrue(midSet.contains(next(items)));
  }

  @Benchmark("Hit in a 49-element set, not identical")
  public void hit() {
    assertTrue(midSet.contains(next(hitItems)));
  }

  @Benchmark("Miss in a 49-element set")
  public void miss() {
    // 49 elements is right before an ArraySet resize
    // 50 elements would be a lot faster due to lower occupancy
    assertFalse(midSet.contains(next(missItems)));
  }

  private <V> V next(V[] array) {
    ++i;
    if (i == array.length) {
      i = 0;
    }
    return array[i];
  }
}
