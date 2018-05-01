package org.alicep.collect;

import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.apache.commons.math3.distribution.ZipfDistribution;
import org.apache.commons.math3.random.Well19937c;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;

@RunWith(BenchmarkRunner.class)
public class SetPerformanceTests<T> {

  static class Config<T> {
    private final Supplier<Set<T>> setFactory;
    private final ItemFactory<T> itemFactory;

    public Config(Supplier<Set<T>> setFactory, ItemFactory<T> itemFactory) {
      this.setFactory = setFactory;
      this.itemFactory = itemFactory;
    }

    @Override
    public String toString() {
      String setType = setFactory.get().getClass().getSimpleName();
      String valueType = itemFactory.createItem(0).getClass().getSimpleName();
      return setType + "<" + valueType + ">";
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

  public SetPerformanceTests(Config<T> config) {
    config.toString();
    this.setFactory = config.setFactory;
    this.itemFactory = config.itemFactory;
  }

  @Benchmark("10 items, many updates")
  public void tenItems_updateHeavy() {
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 10000; index++) {
      if (index >= 10) {
        removePresentItem(arraySet, index - 10);
      }
      addMissingItem(arraySet, index);
    }
  }

  @Benchmark("10 items, many failed lookups")
  public void tenItems_failedLookupHeavy() {
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 2000; index++) {
      if (index >= 10) {
        removePresentItem(arraySet, index - 10);
      }
      addMissingItem(arraySet, index);
      for (long i = index + 1; i < index + 10; i++) {
        assertFalse(arraySet.contains(itemFactory.createItem(i)));
      }
    }
  }

  @Benchmark("10 items, many successful lookup")
  public void tenItems_successfulLookupHeavy() {
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 10; index++) {
      addMissingItem(arraySet, index);
    }
    for (long index = 10; index < 2000; index++) {
      removePresentItem(arraySet, index - 10);
      addMissingItem(arraySet, index);
      for (long i = index - 9; i <= index; i++) {
        assertTrue(arraySet.contains(itemFactory.createItem(i)));
      }
    }
  }

  @Benchmark("400 items, many updates")
  public void fourHundredItems_updateHeavy() {
    // Four hundred items allows a 10-bit word index saving an extra ~15% over 16-bit word indices
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 10000; index++) {
      if (index >= 400) {
        removePresentItem(arraySet, index - 400);
      }
      addMissingItem(arraySet, index);
    }
  }

  @Benchmark("400 items, many failed lookups")
  public void fourHundredItems_failedLookupHeavy() {
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 2000; index++) {
      if (index >= 400) {
        removePresentItem(arraySet, index - 400);
      }
      addMissingItem(arraySet, index);
      for (long i = index + 1; i < index + 10; i++) {
        assertFalse(arraySet.contains(itemFactory.createItem(i)));
      }
    }
  }

  @Benchmark("400 items, many successful lookups")
  public void fourHundredItems_successfulLookupHeavy() {
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 400; index++) {
      addMissingItem(arraySet, index);
    }
    for (long index = 400; index < 2000; index++) {
      removePresentItem(arraySet, index - 400);
      addMissingItem(arraySet, index);
      for (long i = index - 9; i <= index; i++) {
        assertTrue(arraySet.contains(itemFactory.createItem(i)));
      }
    }
  }

  @Benchmark("Between 7 and 100 items, many updates")
  public void between7And100Items_updateHeavy() {
    varyingSizesTest(7, 100, 10000);
  }

  @Benchmark("Between 7 and 10,000 items, many updates")
  public void between7And10000Items_updateHeavy() {
    varyingSizesTest(7, 10000, 100);
  }

  @Benchmark("Between 1,000 and 1,000,000 items, many updates")
  public void between1000And1000000Items_updateHeavy() {
    varyingSizesTest(1000, 1000000, 2);
  }

  private void varyingSizesTest(long min, long max, int waves) {
    Set<T> set = setFactory.get();
    for (int wave = 0; wave < waves; wave++) {
      for (long index = (wave == 0) ? 0 : min + (max - min) * wave; index < min + (max - min) * (wave + 1); index++) {
        addMissingItem(set, index);
      }
      assertEquals(max, set.size());
      for (long index = (max - min) * wave; index < (max - min) * (wave + 1); ++index) {
        removePresentItem(set, index);
      }
      assertEquals(min, set.size());
    }
  }

  @Benchmark("Large cache, weak zipf distribution")
  public void largeCache_weakZipfDistribution() {
    Set<T> set = setFactory.get();
    // > 50% of all accesses will be to the same element.
    // > 90% of all accesses will be to the same ten elements.
    // > 99% of all accesses will be to the same 142 elements.
    ZipfDistribution d = new ZipfDistribution(new Well19937c(-1), 1000, 1.8);
    for (long i = 0; i < 20000; i++) {
      T item = itemFactory.createItem(d.sample());
      set.add(item);
    }
  }

  @Benchmark("Large cache, strong zipf distribution")
  public void largeCache_strongZipfDistribution() {
    Set<T> set = setFactory.get();
    // > 80% of all accesses will be to the same element.
    // > 99% of all accesses will be to the same six elements.
    // > 99.9% of all accesses will be to the same 21 elements.
    ZipfDistribution d = new ZipfDistribution(new Well19937c(-1), 1000, 3);
    for (long i = 0; i < 20000; i++) {
      T item = itemFactory.createItem(d.sample());
      set.add(item);
    }
  }

  @Benchmark("Huge cache, weak zipf distribution")
  public void hugeCache_weakZipfDistribution() {
    Set<T> set = setFactory.get();
    // > 50% of all accesses will be to the same twelve elements.
    // > 90% of all accesses will be to the same 3000 elements.
    ZipfDistribution d = new ZipfDistribution(new Well19937c(-1), 100000, 1.2);
    for (long i = 0; i < 20000; i++) {
      T item = itemFactory.createItem(d.sample());
      set.add(item);
    }
  }

  private void addMissingItem(Set<T> arraySet, long index) {
    T item = itemFactory.createItem(index);
    assertTrue("Failed to insert item #" + (index + 1) + ", " + item,
        arraySet.add(item));
  }

  private void removePresentItem(Set<T> arraySet, long index) {
    T item = itemFactory.createItem(index);
    assertTrue("Failed to remove item #" + (index + 1) + ", " + item,
        arraySet.remove(item));
  }
}
