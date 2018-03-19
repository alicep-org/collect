package org.alicep.collect;

import static java.util.stream.Collectors.toList;
import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.alicep.collect.LongStreams.longs;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.alicep.collect.benchmark.BenchmarkRunner;
import org.alicep.collect.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.collect.benchmark.BenchmarkRunner.Configuration;
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
  private final ItemFactory<T> itemFactory;
  private final Set<T> littleSet;
  private final List<T> elements;

  int i = 0;

  public SmallSetPerformanceTests(Config<T> config) {
    setFactory = config.setFactory;
    itemFactory = config.itemFactory;

    elements = longs(0, 6).mapToObj(itemFactory::createItem).collect(toList());
    littleSet = setFactory.get();
    for (int i = 0; i < 6; i++) {
      littleSet.add(elements.get(i));
    }
  }

  @Benchmark("Create a 6-element set")
  public void create() {
    Set<T> set = setFactory.get();
    elements.forEach(set::add);
  }

  @Benchmark("Iterate through a 6-element set")
  public void iterate() {
    littleSet.forEach(e -> assertNotNull(e));
  }

  @Benchmark("Hit in a 6-element set")
  public void hit() {
    assertTrue(littleSet.contains(itemFactory.createItem(i)));
    if (++i == 6) i = 0;
  }

  @Benchmark("Miss in a 6-element set")
  public void miss() {
    assertFalse(littleSet.contains(itemFactory.createItem(i++ + 6)));
  }
}
