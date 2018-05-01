package org.alicep.collect;

import static org.alicep.collect.ItemFactory.longs;
import static org.alicep.collect.ItemFactory.strings;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@RunWith(BenchmarkRunner.class)
public class MidMapPerformanceTests<K, V> {

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
      new Config<>(() -> new HashMap<>(1000), longs, strings),
      new Config<>(() -> new LinkedHashMap<>(1000), longs, strings),
      new Config<>(() -> ArrayMap.withInitialCapacity(1000), longs, strings),
      new Config<>(() -> new HashMap<>(1000), strings, strings),
      new Config<>(() -> new LinkedHashMap<>(1000), strings, strings),
      new Config<>(() -> ArrayMap.withInitialCapacity(1000), strings, strings));

  private final Supplier<Map<K, V>> mapFactory;
  private final ItemFactory<K> keyFactory;
  private final ItemFactory<V> valueFactory;
  private final Map<K, V> littleMap;
  private final Map<K, V> entries;

  int i = 0;

  public MidMapPerformanceTests(Config<K, V> config) {
    mapFactory = config.mapFactory;
    keyFactory = config.keyFactory;
    valueFactory = config.valueFactory;

    littleMap = mapFactory.get();
    for (int i = 0; i < 1000; i++) {
      littleMap.put(keyFactory.createItem(i), valueFactory.createItem(i));
    }
    entries = ImmutableMap.copyOf(littleMap);
  }

  @Benchmark("Create a 1K-element map")
  public void create() {
    Map<K, V> map = mapFactory.get();
    entries.forEach((k, v) -> map.put(k, v));
  }

  @Benchmark("Iterate through a 1K-element map")
  public void iterate() {
    littleMap.forEach((k, v) -> assertNotNull(v));
  }

  @Benchmark("get: Hit in a 1K-element map")
  public void getHit() {
    assertNotNull(littleMap.get(keyFactory.createItem(i)));
    if (++i == 1000) i = 0;
  }

  @Benchmark("get: Miss in a 1K-element map")
  public void getMiss() {
    assertNull(littleMap.get(keyFactory.createItem(i++ + 1000)));
  }

  @Benchmark("containsKey: Hit in a 1K-element map")
  public void containsKeyHit() {
    assertTrue(littleMap.containsKey(keyFactory.createItem(i)));
    if (++i == 1000) i = 0;
  }

  @Benchmark("containsKey: Miss in a 1K-element map")
  public void containsKeyMiss() {
    assertFalse(littleMap.containsKey(keyFactory.createItem(i++ + 1000)));
  }
}
