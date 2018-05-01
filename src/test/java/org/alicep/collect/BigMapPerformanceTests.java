package org.alicep.collect;

import static java.util.stream.Collectors.toList;
import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.alicep.collect.LongStreams.longs;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;

@RunWith(BenchmarkRunner.class)
public class BigMapPerformanceTests<K, V> {

  static class Config<K, V> {
    private final Supplier<Map<K, V>> mapFactory;
    private final ItemFactory<K> keyFactory;
    private final ItemFactory<V> valueFactory;

    public Config(
        Supplier<Map<K, V>> mapFactory,
        ItemFactory<K> keyFactory,
        ItemFactory<V> valueFactory) {
      this.mapFactory = mapFactory;
      this.keyFactory = keyFactory;
      this.valueFactory = valueFactory;
    }

    @Override
    public String toString() {
      String mapType = mapFactory.get().getClass().getSimpleName();
      String keyType = keyFactory.createItem(0).getClass().getSimpleName();
      String valueType = valueFactory.createItem(0).getClass().getSimpleName();
      return mapType + "<" + keyType + ", " + valueType + ">";
    }
  }

  @Configuration
  public static final ImmutableList<Config<?, ?>> CONFIGURATIONS = ImmutableList.of(
      new Config<>(HashMap::new, longs, strings),
      new Config<>(LinkedHashMap::new, longs, strings),
      new Config<>(ArrayMap::new, longs, strings),
      new Config<>(HashMap::new, strings, strings),
      new Config<>(LinkedHashMap::new, strings, strings),
      new Config<>(ArrayMap::new, strings, strings));

  private final Supplier<Map<K, V>> mapFactory;
  private final ItemFactory<K> keyFactory;
  private final ItemFactory<V> valueFactory;
  private final Map<K, V> bigMap;
  private List<K> keys = null;
  private List<V> values = null;

  private int i = 0;

  public BigMapPerformanceTests(Config<K, V> config) {
    mapFactory = config.mapFactory;
    keyFactory = config.keyFactory;
    valueFactory = config.valueFactory;

    keys = longs(0, 1_000_000).mapToObj(keyFactory).collect(toList());
    values = longs(0, 1_000_000).mapToObj(valueFactory).collect(toList());
    bigMap = mapFactory.get();
    for (int i = 0; i < 1_000_000; ++i) {
      bigMap.put(keys.get(i), values.get(i));
    }
  }

  @Benchmark("Create a 1M-element map")
  public void create() {
    Map<K, V> map = mapFactory.get();
    for (int i = 0; i < 1_000_000; ++i) {
      map.put(keys.get(i), values.get(i));
    }
  }

  @Benchmark("Iterate through a 1M-element map")
  public void iterate() {
    bigMap.forEach((k, v) -> assertNotNull(v));
  }

  @Benchmark("Hit in a 1M-element map")
  public void hit() {
    assertNotNull(bigMap.get(keyFactory.createItem(i)));
    if (++i == 1_000_000) i = 0;
  }

  @Benchmark("Miss in a 1M-element map")
  public void miss() {
    assertNull(bigMap.get(keyFactory.createItem(i + 1_000_000)));
    i++;
  }

  @Benchmark("Miss 29 times out of 30 in a 1M-element map")
  public void mainlyMiss() {
    for (long j = 0; j < 30; ++j) {
      bigMap.get(keyFactory.createItem(i + j * 1_000_000));
    }
    if (++i == 1_000_000) i = 0;
  }
}
