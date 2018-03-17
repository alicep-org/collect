/*
 * Copyright 2016 Chris Purcell. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.alicep.collect;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import com.google.common.base.Objects;

/**
 * <p>Hash table and array implementation of the {@link Map} interface,
 * with predictable iteration order.  This implementation is similar to
 * {@link LinkedHashMap}, but uses a more compact memory representation
 * <a href="https://morepypy.blogspot.co.uk/2015/01/faster-more-memory-efficient-and-more.html"
 * >originally pioneered by PyPy</a>, and subsequently
 * <a href="https://docs.python.org/3.6/whatsnew/3.6.html#whatsnew36-compactdict"
 * >adopted in Python 3.6</a>.
 *
 * <p>This class provides all of the optional <tt>Map</tt> operations, and
 * permits null elements.  Like {@link HashMap}, it provides constant-time
 * performance for the basic operations (<tt>add</tt>, <tt>contains</tt> and
 * <tt>remove</tt>), assuming the hash function disperses elements
 * properly among the buckets.  Performance is typically within 5% of
 * <tt>HashMap</tt>, with only around a half of the memory overhead
 * (a third of <tt>LinkedHashMap</tt>).
 *
 * <p>Unlike <tt>HashMap</tt> and <tt>LinkedHashMap</tt>, this class does not cache the
 * {@linkplain Object#hashCode() hash code value} of its keys, as this is typically
 * redundant: value types are a trivial transformation, while Strings already cache
 * their hash values.  This may however result in a significant negative performance
 * impact if key hashCode/equality checks are expensive.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access an AraryMap concurrently, and at least
 * one of the threads modifies the map structurally, it <em>must</em> be
 * synchronized externally.  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the map.
 *
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedMap Collections.synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map:<pre>
 *   Map&lt;...&gt; m = Collections.synchronizedMap(new ArrayMap&lt;&gt;(...));</pre>
 *
 * A structural modification is any operation that adds or deletes one or more
 * mappings or, in the case of access-ordered linked hash maps, affects
 * iteration order.  Merely changing the value associated with a key that is
 * already contained in the map is not a structural modification.
 *
 * <p>The iterators returned by the <tt>iterator</tt> method of the collections
 * returned by all of this class's collection view methods are
 * <em>fail-fast</em>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * <tt>remove</tt> method, the iterator will throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw <tt>ConcurrentModificationException</tt> on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @see     Map
 * @see     HashMap
 * @see     TreeMap
 */
public class ArrayMap<K, V> extends AbstractMap<K, V> implements Serializable {

  private enum Reserved { NULL }
  private static final int NO_INDEX = -1;
  private static final int DEFAULT_CAPACITY = 10;

  private int size = 0;
  private int modCount = 0;
  private Object[] objects;
  // Will always point just past the last object in the array
  private int head = 0;
  private long[] lookup;

  private static int log2ceil(int value) {
    return 32 - Integer.numberOfLeadingZeros(value - 1);
  }

  /**
   * Constructs an empty map with an initial capacity of ten.
   */
  public ArrayMap() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * Constructs an empty map with the specified initial capacity.
   *
   * @param  initialCapacity  the initial capacity of the map
   * @return an empty map
   * @throws IllegalArgumentException if the specified initial capacity
   *         is negative
   *
   * @param <K> the type of keys maintained by the map
   * @param <V> the type of mapped values
   */
  public static <K, V> ArrayMap<K, V> withInitialCapacity(int initialCapacity) {
    return new ArrayMap<>(initialCapacity);
  }

  /**
   * Constructs a map with the same mappings as the specified map.
   *
   * @param  entries the map whose mappings are to be placed in this map
   * @throws NullPointerException if the specified map is null
   */
  public ArrayMap(Map<? extends K, ? extends V> entries) {
    this(entries.size());
    putAll(entries);
  }

  private ArrayMap(int initialCapacity) {
    checkArgument(initialCapacity >= 0, "initialCapacity must be non-negative");
    objects = new Object[2 * Math.max(initialCapacity, DEFAULT_CAPACITY)];
    lookup = newLookupArray();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean containsKey(Object key) {
    Object comparisonObject = (key == null) ? Reserved.NULL : key;
    long index = lookup(comparisonObject);
    return (index >= 0);
  }

  @Override
  public V get(Object key) {
    Object comparisonObject = (key == null) ? Reserved.NULL : key;
    long index = lookup(comparisonObject);
    if (index >= 0) {
      @SuppressWarnings("unchecked")
      V value = (V) objects[(int) index * 2 + 1];
      return value;
    }
    return null;
  }

  @Override
  public V put(K key, V value) {
    Object insertionObject = firstNonNull(key, Reserved.NULL);

    long lookupIndex = lookup(insertionObject);
    if (lookupIndex >= 0) {
      @SuppressWarnings("unchecked")
      V oldValue = (V) objects[(int) lookupIndex * 2 + 1];
      objects[(int) lookupIndex * 2 + 1] = value;
      return oldValue;
    }

    modCount++;
    if (ensureFreeCell()) {
      lookupIndex = lookup(insertionObject);
    }
    int index = head++;
    objects[index * 2] = insertionObject;
    objects[index * 2 + 1] = value;
    addLookup((int) -(lookupIndex + 1), index);

    size++;
    return null;
  }

  @Override
  public V remove(Object o) {
    long index = lookup((o == null) ? Reserved.NULL : o);
    if (index < 0) {
      return null;
    }

    @SuppressWarnings("unchecked")
    V oldValue = (V) objects[(int) index * 2 + 1];
    deleteObjectAtIndex((int) index);
    return oldValue;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    checkNotNull(action);
    for (int i = 0; i < head; ++i) {
      @SuppressWarnings("unchecked")
      K key = (K) objects[i * 2];
      @SuppressWarnings("unchecked")
      V value = (V) objects[i * 2 + 1];
      if (key != null) {
        action.accept(key == Reserved.NULL ? null : key, value);
      }
    }
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new EntrySet();
  }

  /* Lookup methods */

  private int lookupEntryBits() {
    return log2ceil(objects.length) - 1;
  }

  private int lookupEntriesPerLong() {
    return Long.SIZE / lookupEntryBits();
  }

  private long[] newLookupArray() {
    // Aim for a power of two with 50% occupancy maximum
    int numCells = 1 << log2ceil(objects.length);
    while (objects.length > numCells) {
      numCells = numCells * 2;
    }
    int cellsPerLong = lookupEntriesPerLong();
    long[] lookup = new long[1 + (numCells - 1) / cellsPerLong];
    Arrays.fill(lookup, -1);
    return lookup;
  }

  private void addLookup(int lookupIndex, int index) {
    assertState(index != NO_INDEX, "Invalid index");
    if (lookupEntryBits() < Long.SIZE) {
      addLookupNibble(lookupIndex, index);
    } else {
      lookup[lookupIndex] = index;
    }
  }

  private long lookupMask() {
    return (1 << lookupEntryBits()) - 1;
  }

  private void addLookupNibble(int lookupIndex, int index) {
    long word = lookup[lookupIndex / lookupEntriesPerLong()];
    int shift = lookupEntryBits() * (lookupIndex % lookupEntriesPerLong());
    word &= ~(lookupMask() << shift);
    word |= (index & lookupMask()) << shift;
    lookup[lookupIndex / lookupEntriesPerLong()] = word;
  }

  /**
   * If {@code obj} is in the {@code objects} array, returns its index; otherwise, returns
   * {@code (-(probe insertion point) - 1)}, where "probe insertion point" is
   * the index of first free cell in {@code lookup} along the probe sequence for {@code obj}.
   */
  private long lookup(Object obj) {
    int mask = numLookupCells() - 1;
    int tombstoneIndex = -1;
    int lookupIndex = obj.hashCode();
    int stride = Integer.reverse(lookupIndex) * 2 + 1;
    lookupIndex &= mask;
    stride &= mask;
    int index;
    while ((index = getLookupAt(lookupIndex)) != NO_INDEX) {
      Object other = objects[index * 2];
      if (other == null) {
        if (tombstoneIndex == -1) {
          tombstoneIndex = lookupIndex;
        }
      } else if (other.equals(obj)) {
        return index;
      }
      lookupIndex += stride;
      lookupIndex &= mask;
    }
    if (tombstoneIndex != -1) {
      return -tombstoneIndex - 1;
    } else {
      return -lookupIndex - 1;
    }
  }

  private int numLookupCells() {
    return Integer.highestOneBit(lookup.length * lookupEntriesPerLong());
  }

  private int getLookupAt(int lookupIndex) {
    long word = lookup[lookupIndex / lookupEntriesPerLong()];
    int shift = lookupEntryBits() * (lookupIndex % lookupEntriesPerLong());
    int value = (int) ((word >> shift) & lookupMask());
    return (value == (NO_INDEX & lookupMask())) ? -1 : value;
  }

  private void clearLookupArray() {
    Arrays.fill(lookup, -1);
  }

  /* Other internal methods */

  private boolean ensureFreeCell() {
    if (objects.length == head * 2) {
      if (size >= minGrowthThreshold()) {
        int newSize = (objects.length >> 1) + (objects.length >> 2);
        objects = Arrays.copyOf(objects, newSize * 2);
        lookup = null;
      }
      compact();
      return true;
    }
    return false;
  }

  private void deleteObjectAtIndex(int index) {
    checkState(objects[index * 2] != null);
    assertState(size != 0, "Size is 0 but a cell is not empty");
    objects[index * 2] = null;
    objects[index * 2 + 1] = null;
    size--;
    modCount++;
    if (index == head - 1) {
      head--;
      while (head > 0 && objects[head * 2 - 2] == null) {
        head--;
      }
    }
  }

  private void compact() {
    if (lookup == null) {
      lookup = newLookupArray();
    } else {
      clearLookupArray();
    }
    int target = 0;
    for (int source = 0; source < objects.length / 2; source++) {
      Object e = objects[source * 2];
      if (e == null) {
        continue;
      }
      if (source != target) {
        objects[target * 2] = e;
        objects[target * 2 + 1] = objects[source * 2 + 1];
      }
      long freeLookupCell = -(lookup(e) + 1);
      assertState(freeLookupCell >= 0, "Free lookup cell is negative (%s)", freeLookupCell);
      addLookup((int) freeLookupCell, target);
      target++;
    }
    Arrays.fill(objects, target * 2, objects.length, null);
    head = size;
  }

  private int minGrowthThreshold() {
    // Grow the objects array if less than a quarter of it is DELETED tombstones when it fills up.
    return objects.length * 3 / 8;
  }

  private static void assertState(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new AssertionError(String.format(message, args));
    }
  }

  /* Serialization */

  private static final long serialVersionUID = 0;

  private void writeObject(java.io.ObjectOutputStream s) throws IOException {
    s.writeInt(size);
    for (int i = 0; i < head * 2; ++i) {
      Object o = objects[i * 2];
      if (o != null) {
        s.writeObject(o == Reserved.NULL ? null : o);
        s.writeObject(objects[i * 2 + 1]);
      }
    }
  }

  private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
    size = s.readInt();
    objects = new Object[Math.max(size, DEFAULT_CAPACITY) * 2];
    lookup = newLookupArray();
    clearLookupArray();
    for (head = 0; head < size; head++) {
      Object e = firstNonNull(s.readObject(), Reserved.NULL);
      objects[head * 2] = e;
      objects[head * 2 + 1] = s.readObject();
      long x = lookup(e);
      long freeLookupCell = -(x + 1);
      if (freeLookupCell < 0) {
        throw new StreamCorruptedException("Duplicate data found in serialized map");
      }
      addLookup((int) freeLookupCell, head);
    }
  }

  /* Entry set */

  private class EntrySet extends AbstractSet<Entry<K, V>> {

    @Override
    public Iterator<Entry<K, V>> iterator() {
      return new IteratorImpl();
    }

    @Override
    public Spliterator<java.util.Map.Entry<K, V>> spliterator() {
      return Spliterators.spliterator(
          this, Spliterator.SIZED | Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    @Override
    public int size() {
      return size;
    }
  }

  /* Iteration */

  private class IteratorImpl implements Iterator<Entry<K, V>> {
    private int validAt;
    private int index;

    IteratorImpl() {
      validAt = modCount;
      index = -1;
    }

    @Override
    public boolean hasNext() {
      if (modCount != validAt) {
        throw new ConcurrentModificationException();
      }
      return index < head - 1;
    }

    @Override
    public Entry<K, V> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      do {
        index++;
      } while (index < head && objects[index * 2] == null);

      assertState(index < head, "Head in invalid position");

      return new EntryImpl(index);
    }

    @Override
    public void remove() {
      checkState(index != -1);
      if (modCount != validAt) {
        throw new ConcurrentModificationException();
      }
      deleteObjectAtIndex(index);
      validAt = modCount;
    }

    private class EntryImpl implements Entry<K, V> {
      private final int index;

      EntryImpl(int index) {
        this.index = index;
      }

      @Override
      public K getKey() {
        if (modCount != validAt) {
          throw new ConcurrentModificationException();
        }
        Object keyObject = objects[index * 2];
        @SuppressWarnings("unchecked")
        K key = (keyObject == Reserved.NULL) ? null : (K) keyObject;
        return key;
      }

      @Override
      public V getValue() {
        if (modCount != validAt) {
          throw new ConcurrentModificationException();
        }
        @SuppressWarnings("unchecked")
        V value = (V) objects[index * 2 + 1];
        return value;
      }

      @Override
      public V setValue(V value) {
        if (modCount != validAt) {
          throw new ConcurrentModificationException();
        }
        @SuppressWarnings("unchecked")
        V oldValue = (V) objects[index * 2 + 1];
        objects[index * 2 + 1] = value;
        ++modCount;
        ++validAt;
        return oldValue;
      }

      /**
       * Compares the specified object with this entry for equality.
       * Returns <tt>true</tt> if the given object is also a map entry and
       * the two entries represent the same mapping.  More formally, two
       * entries <tt>e1</tt> and <tt>e2</tt> represent the same mapping
       * if<pre>
       *     (e1.getKey()==null ?
       *      e2.getKey()==null : e1.getKey().equals(e2.getKey()))  &amp;&amp;
       *     (e1.getValue()==null ?
       *      e2.getValue()==null : e1.getValue().equals(e2.getValue()))
       * </pre>
       * This ensures that the <tt>equals</tt> method works properly across
       * different implementations of the <tt>Map.Entry</tt> interface.
       *
       * @param o object to be compared for equality with this map entry
       * @return <tt>true</tt> if the specified object is equal to this map
       *         entry
       */
      @Override
      public boolean equals(Object o) {
        if (!(o instanceof Entry)) {
          return false;
        }
        Entry<?, ?> other = (Entry<?, ?>) o;
        return Objects.equal(getKey(), other.getKey()) && Objects.equal(getValue(), other.getValue());
      }

      /**
       * Returns the hash code value for this map entry.  The hash code
       * of a map entry <tt>e</tt> is defined to be: <pre>
       *     (e.getKey()==null   ? 0 : e.getKey().hashCode()) ^
       *     (e.getValue()==null ? 0 : e.getValue().hashCode())
       * </pre>
       * This ensures that <tt>e1.equals(e2)</tt> implies that
       * <tt>e1.hashCode()==e2.hashCode()</tt> for any two Entries
       * <tt>e1</tt> and <tt>e2</tt>, as required by the general
       * contract of <tt>Object.hashCode</tt>.
       *
       * @return the hash code value for this map entry
       * @see Object#hashCode()
       * @see Object#equals(Object)
       * @see #equals(Object)
       */
      @Override
      public int hashCode() {
        Object key = objects[index * 2];
        Object value = objects[index * 2 + 1];
        return (key == Reserved.NULL ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
      }

      @Override
      public String toString() {
        return getKey() + "=" + getValue();
      }
    }
  }
}
