package org.alicep.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import org.apache.commons.math3.distribution.ZipfDistribution;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Ignore("Manual performance tests")
@RunWith(Parameterized.class)
public class SetPerformanceTests<T> {

  private static final HashFunction hashing = Hashing.sha256();
  private static final ItemFactory<Long> longs = index -> hashing.hashLong(index).asLong();
  private static final ItemFactory<String> strings = index -> hashing.hashLong(index).toString();

  private interface ItemFactory<T> {
    T createItem(long index);
  }

  public static class Config<T> {
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

  private static final ImmutableList<Config<?>> CONFIGURATIONS = ImmutableList.of(
      new Config<>(HashSet::new, longs),
      new Config<>(LinkedHashSet::new, longs),
      new Config<>(ArraySet::new, longs),
      new Config<>(HashSet::new, strings),
      new Config<>(LinkedHashSet::new, strings),
      new Config<>(ArraySet::new, strings));

  private final String configName;
  private final Supplier<Set<T>> setFactory;
  private final ItemFactory<T> itemFactory;

  @Parameters(name= "{0}")
  public static Iterable<Config<?>> data() {
      return CONFIGURATIONS;
  }

  public SetPerformanceTests(Config<T> config) {
    this.configName = config.toString();
    this.setFactory = config.setFactory;
    this.itemFactory = config.itemFactory;
  }

  @Test
  public void tenItems_updateHeavy() {
    System.out.println(" ** ten items, update heavy: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> arraySet = setFactory.get();
      for (long index = 0; index < 1000000; index++) {
        if (index >= 10) {
          removePresentItem(arraySet, index - 10);
        }
        addMissingItem(arraySet, index);
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void tenItems_failedLookupHeavy() {
    System.out.println(" ** ten items, failed-lookup heavy: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> arraySet = setFactory.get();
      for (long index = 0; index < 200000; index++) {
        if (index >= 10) {
          removePresentItem(arraySet, index - 10);
        }
        addMissingItem(arraySet, index);
        for (long i = index + 1; i < index + 10; i++) {
          assertFalse(arraySet.contains(itemFactory.createItem(i)));
        }
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void tenItems_successfulLookupHeavy() {
    System.out.println(" ** ten items, successful-lookup heavy: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> arraySet = setFactory.get();
      for (long index = 0; index < 10; index++) {
        addMissingItem(arraySet, index);
      }
      for (long index = 10; index < 200000; index++) {
        removePresentItem(arraySet, index - 10);
        addMissingItem(arraySet, index);
        for (long i = index - 9; i <= index; i++) {
          assertTrue(arraySet.contains(itemFactory.createItem(i)));
        }
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void fourHundredItems_updateHeavy() {
    // Four hundred items allows a 10-bit word index saving an extra ~15% over 16-bit word indices
    System.out.println(" ** 400 items, update heavy: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> arraySet = setFactory.get();
      for (long index = 0; index < 1000000; index++) {
        if (index >= 400) {
          removePresentItem(arraySet, index - 400);
        }
        addMissingItem(arraySet, index);
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void fourHundredItems_failedLookupHeavy() {
    System.out.println(" ** 400 items, failed-lookup heavy: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> arraySet = setFactory.get();
      for (long index = 0; index < 200000; index++) {
        if (index >= 400) {
          removePresentItem(arraySet, index - 400);
        }
        addMissingItem(arraySet, index);
        for (long i = index + 1; i < index + 10; i++) {
          assertFalse(arraySet.contains(itemFactory.createItem(i)));
        }
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void fourHundredItems_successfulLookupHeavy() {
    System.out.println(" ** 400 items, successful-lookup heavy: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> arraySet = setFactory.get();
      for (long index = 0; index < 400; index++) {
        addMissingItem(arraySet, index);
      }
      for (long index = 400; index < 200000; index++) {
        removePresentItem(arraySet, index - 400);
        addMissingItem(arraySet, index);
        for (long i = index - 9; i <= index; i++) {
          assertTrue(arraySet.contains(itemFactory.createItem(i)));
        }
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void between7And100Items_updateHeavy() {
    varyingSizesTest(7, 100, 10000);
  }

  @Test
  public void between7And10000Items_updateHeavy() {
    varyingSizesTest(7, 10000, 100);
  }

  @Test
  public void between1000And1000000Items_updateHeavy() {
    varyingSizesTest(1000, 1000000, 2);
  }

  private void varyingSizesTest(long min, long max, int waves) {
    System.out.println(" ** " + min + "–" + max + " items: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
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
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void largeCache_weakZipfDistribution() {
    System.out.println(" ** large cache, weak zipf distribution: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> set = setFactory.get();
      // > 50% of all accesses will be to the same element.
      // > 90% of all accesses will be to the same ten elements.
      // > 99% of all accesses will be to the same 142 elements.
      ZipfDistribution d = new ZipfDistribution(new Well19937c(-1), 1000, 1.8);
      for (long i = 0; i < 2000000; i++) {
        T item = itemFactory.createItem(d.sample());
        set.add(item);
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void largeCache_strongZipfDistribution() {
    System.out.println(" ** large cache, strong zipf distribution: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> set = setFactory.get();
      // > 80% of all accesses will be to the same element.
      // > 99% of all accesses will be to the same six elements.
      // > 99.9% of all accesses will be to the same 21 elements.
      ZipfDistribution d = new ZipfDistribution(new Well19937c(-1), 1000, 3);
      for (long i = 0; i < 2000000; i++) {
        T item = itemFactory.createItem(d.sample());
        set.add(item);
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  @Test
  public void hugeCache_weakZipfDistribution() {
    System.out.println(" ** huge cache, weak zipf distribution: " + configName + " ** ");
    for (int run = 0; run < 3; run++) {
      Stopwatch watch = Stopwatch.createStarted();
      Set<T> set = setFactory.get();
      // > 50% of all accesses will be to the same twelve elements.
      // > 90% of all accesses will be to the same 3000 elements.
      ZipfDistribution d = new ZipfDistribution(new Well19937c(-1), 100000, 1.2);
      for (long i = 0; i < 2000000; i++) {
        T item = itemFactory.createItem(d.sample());
        set.add(item);
      }
      watch.stop();
      System.out.println("Run #" + (run + 1) + " took " + watch.elapsed(TimeUnit.MILLISECONDS) + " ms");
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
