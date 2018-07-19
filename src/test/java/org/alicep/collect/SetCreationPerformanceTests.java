package org.alicep.collect;

import static java.util.Arrays.setAll;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.junit.runner.RunWith;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@RunWith(BenchmarkRunner.class)
public class SetCreationPerformanceTests {

  static class Config implements Comparable<Config> {
    private final Supplier<Set<String>> setFactory;
    private final int size;
    private final ItemFactory<String> itemFactory = ItemFactory.strings;

    public Config(Supplier<Set<String>> setFactory, int size) {
      this.setFactory = setFactory;
      this.size = size;
    }

    @Override
    public String toString() {
      return setName() + ", " + size + " elements";
    }

    private String setName() {
      return setFactory.get().getClass().getSimpleName();
    }

    @Override
    public int compareTo(Config o) {
      return ComparisonChain.start()
          .compare(size, o.size)
          .compare(setName(), o.setName())
          .result();
    }
  }

  @Configuration
  public static final List<Config> CONFIGURATIONS = configs();

  @SuppressWarnings("unchecked")
  private static List<Config> configs() {
    List<Supplier<Set<String>>> factories = ImmutableList.of(BucketPairSpikeArraySet::new, HashSet::new, LinkedHashSet::new);
    List<Integer> sizes = ImmutableList.of(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 21, 22,
        23, 24, 25, 32, 33,
        34, 35, 40, 48, 49,
        50, 51, 61, 71, 72, 73,
        74, 109,
        110, 163,
        164, 244,
        245, 366,
        367, 549,
        550, 823,
        824, 1234,
        1235, 1851,
        1852, 2776,
        2777, 4164,
        4165, 6246,
        6247, 9369,
        10_000, 100_000);
    return Lists.transform(Lists.cartesianProduct(factories, sizes), l -> new Config(
        (Supplier<Set<String>>) l.get(0),
        (int) l.get(1)));
  }

  private final Supplier<Set<String>> setFactory;
  private Set<String> set;

  private final String[] items;

  int i = 0;

  public SetCreationPerformanceTests(Config config) {
    setFactory = config.setFactory;
    items = new String[config.size];
    setAll(items, config.itemFactory::createItem);
  }

  @Benchmark("Create a set")
  public void create() {
    set = setFactory.get();
    for (String item : items) {
      set.add(item);
    }
  }
}
