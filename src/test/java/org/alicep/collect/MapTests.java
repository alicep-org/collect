package org.alicep.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Map;
import java.util.function.Supplier;

@RunWith(Parameterized.class)
@FixMethodOrder(MethodSorters.JVM)
public class MapTests<T> {

  private static final HashFunction md5 = Hashing.sha256();
  private static final ItemFactory<Long> longs = index -> md5.hashLong(index).asLong();
  private static final ItemFactory<String> strings = index -> md5.hashLong(index).toString();

  private interface ItemFactory<T> {
    T createItem(long index);
  }

  public static class Config<T> {
    private final Supplier<Map<T,T>> mapFactory;
    private final ItemFactory<T> itemFactory;
    private final int size;

    public Config(Supplier<Map<T,T>> mapFactory, ItemFactory<T> itemFactory, int size) {
      this.mapFactory = mapFactory;
      this.itemFactory = itemFactory;
      this.size = size;
    }

    @Override
    public String toString() {
      String setType = mapFactory.get().getClass().getSimpleName();
      String valueType = itemFactory.createItem(0).getClass().getSimpleName();
      return setType + "<" + valueType + ">, " + size + " elements";
    }
  }

  private static final ImmutableList<Integer> SIZES = ImmutableList.of(10, 15, 33, 75, 290, 375);

  @Parameters(name= "{0}")
  public static Iterable<Config<?>> data() {
    ImmutableList.Builder<Config<?>> data = ImmutableList.builder();
    for (int size : SIZES) {
      data.add(new Config<>(CompactMap::new, longs, size));
      data.add(new Config<>(CompactMap::new, strings, size));
    }
    return data.build();
  }

  private final Supplier<Map<T,T>> mapFactory;
  private final ItemFactory<T> itemFactory;
  private final int size;

  public MapTests(Config<T> config) {
    this.mapFactory = config.mapFactory;
    this.itemFactory = config.itemFactory;
    this.size = config.size;
  }

  @Test
  public void validateAThousandInsertions() {
    Map<T,T> compactSet = mapFactory.get();
    for (long index = 0; index < 1000; index++) {
      if (index >= size) {
        removePresentItem(compactSet, index - size);
        verifyItems(compactSet, index - size + 1, index - 1);
      }
      addMissingItem(compactSet, index);
      verifyItems(compactSet, Math.max(index - size + 1, 0), index);
    }
  }

  @Test
  public void validateAThousandUpdates() {
    Map<T,T> compactSet = mapFactory.get();
    for (long index = 0; index < 1000; ++index) {
      T key = itemFactory.createItem(index % size);
      T value = itemFactory.createItem(index);
      T expectedOldValue = (index < size) ? null : itemFactory.createItem(index - size);
      assertEquals("Wrong value when putting item #" + (index + 1) + " at " + key,
          expectedOldValue, compactSet.put(key, value));
    }
  }

  private void addMissingItem(Map<T,T> compactSet, long index) {
    T key = itemFactory.createItem(index);
    T value = itemFactory.createItem(index + 100000);
    assertNull("Value already inserted for item #" + (index + 1) + ", " + key,
        compactSet.put(key, value));
  }

  private void removePresentItem(Map<T,T> compactSet, long index) {
    T item = itemFactory.createItem(index);
    T value = itemFactory.createItem(index + 100000);
    assertEquals("Wrong value when removing item #" + (index + 1) + ", " + item,
        value, compactSet.remove(item));
  }

  private void verifyItems(Map<T,T> compactSet, long firstIndex, long lastIndex) {
    assertTrue(lastIndex >= firstIndex);
    for (long index = firstIndex - 20; index < firstIndex; index++) {
      assertNull("Regained item " + (index + 1), compactSet.get(itemFactory.createItem(index)));
    }
    for (long index = firstIndex; index <= lastIndex; index++) {
      T value = itemFactory.createItem(index + 100000);
      assertEquals("Wrong/lost item " + (index + 1) + " in range [" + (firstIndex + 1) + ", " + (lastIndex + 1) + "]",
          value, compactSet.get(itemFactory.createItem(index)));
    }
    for (long index = lastIndex + 1; index <= lastIndex + 20; index++) {
      assertNull("Unexpected item " + (index + 1), compactSet.get(itemFactory.createItem(index)));
    }
  }
}
