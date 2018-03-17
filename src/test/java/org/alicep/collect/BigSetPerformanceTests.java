package org.alicep.collect;

import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.alicep.collect.BenchmarkRunner.Benchmark;
import org.alicep.collect.BenchmarkRunner.Configuration;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;

@RunWith(BenchmarkRunner.class)
public class BigSetPerformanceTests<T> {

  private static class Config<T> {
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
  private List<T> elements = null;

  int i = 0;

  public BigSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;
    itemFactory = config.itemFactory;

    bigSet = setFactory.get();
    for (int i = 0; i < 1_000_000; i++) {
      bigSet.add(itemFactory.createItem(i));
    }
  }

  @Benchmark("Create a 1M-element map")
  public void create() {
    if (elements == null) {
      elements = new ArrayList<>(bigSet);
      bigSet = null;
    }
    Set<T> set = setFactory.get();
    elements.forEach(set::add);
  }

  @Benchmark("Iterate through a 1M-element map")
  public void iterate() {
    bigSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 1M-element map")
  public void hit() {
    assertTrue(bigSet.contains(itemFactory.createItem(i)));
    if (++i == 1_000_000) i = 0;
  }

  @Benchmark("Miss in a 1M-element map")
  public void miss() {
    assertFalse(bigSet.contains(itemFactory.createItem(i++ + 1_000_000)));
  }
}
