package org.alicep.collect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Set;
import java.util.function.Supplier;

@RunWith(Parameterized.class)
public class SetTests<T> {

  private static final HashFunction md5 = Hashing.sha256();
  private static final ItemFactory<Long> longs = index -> md5.hashLong(index).asLong();
  private static final ItemFactory<String> strings = index -> md5.hashLong(index).toString();

  private interface ItemFactory<T> {
    T createItem(long index);
  }

  public static class Config<T> {
    private final Supplier<Set<T>> setFactory;
    private final ItemFactory<T> itemFactory;
    private final int size;

    public Config(Supplier<Set<T>> setFactory, ItemFactory<T> itemFactory, int size) {
      this.setFactory = setFactory;
      this.itemFactory = itemFactory;
      this.size = size;
    }

    @Override
    public String toString() {
      String setType = setFactory.get().getClass().getSimpleName();
      String valueType = itemFactory.createItem(0).getClass().getSimpleName();
      return setType + "<" + valueType + ">, " + size + " elements";
    }
  }

  private static final ImmutableList<Integer> SIZES = ImmutableList.of(10, 15, 33, 75, 290, 375);

  @Parameters(name= "{0}")
  public static Iterable<Config<?>> data() {
    ImmutableList.Builder<Config<?>> data = ImmutableList.builder();
    for (int size : SIZES) {
      data.add(new Config<>(ArraySet::new, longs, size));
      data.add(new Config<>(ArraySet::new, strings, size));
    }
    return data.build();
  }

  private final Supplier<Set<T>> setFactory;
  private final ItemFactory<T> itemFactory;
  private final int size;

  public SetTests(Config<T> config) {
    this.setFactory = config.setFactory;
    this.itemFactory = config.itemFactory;
    this.size = config.size;
  }

  @Test
  public void validateAThousandInsertions() {
    Set<T> arraySet = setFactory.get();
    for (long index = 0; index < 1000; index++) {
      if (index >= size) {
        removePresentItem(arraySet, index - size);
        verifyItems(arraySet, index - size + 1, index - 1, "after removing item " + (index - size + 1));
      }
      addMissingItem(arraySet, index);
      verifyItems(arraySet, Math.max(index - size + 1, 0), index, "after adding item " + (index + 1));
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

  private void verifyItems(Set<T> arraySet, long firstIndex, long lastIndex, String when) {
    assertTrue(lastIndex >= firstIndex);
    for (long index = firstIndex - 20; index < firstIndex; index++) {
      assertFalse("Regained item " + (index + 1) + " " + when, arraySet.contains(itemFactory.createItem(index)));
    }
    for (long index = firstIndex; index <= lastIndex; index++) {
      assertTrue("Lost item " + (index + 1) + " " + when, arraySet.contains(itemFactory.createItem(index)));
    }
    for (long index = lastIndex + 1; index <= lastIndex + 20; index++) {
      assertFalse("Unexpected item " + (index + 1) + " " + when, arraySet.contains(itemFactory.createItem(index)));
    }
  }
}
