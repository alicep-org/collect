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
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collector;

/**
 * <p>Hash table and array implementation of the {@link Set} interface,
 * with predictable iteration order.  This implementation is similar to
 * {@link LinkedHashSet}, but uses a more compact memory representation
 * <a href="https://morepypy.blogspot.co.uk/2015/01/faster-more-memory-efficient-and-more.html"
 * >originally pioneered by PyPy</a>, and subsequently
 * <a href="https://docs.python.org/3.6/whatsnew/3.6.html#whatsnew36-compactdict"
 * >adopted in Python 3.6</a>.
 *
 * <p>This class provides all of the optional <tt>Set</tt> operations, and
 * permits null elements.  Like {@link HashSet}, it provides constant-time
 * performance for the basic operations (<tt>add</tt>, <tt>contains</tt> and
 * <tt>remove</tt>), assuming the hash function disperses elements
 * properly among the buckets.  Performance is typically within 5% of
 * <tt>HashSet</tt>, with only around a third of the memory overhead
 * (a quarter of <tt>LinkedHashSet</tt>, and close to a plain {@link ArrayList}).
 *
 * <p>Unlike <tt>HashSet</tt> and <tt>LinkedHashSet</tt>, this class does not cache the
 * {@linkplain Object#hashCode() hash code value} of its elements, as this is typically
 * redundant: numeric types are a trivial transformation, while Strings already cache
 * their hash values.  This may however result in a significant negative performance
 * impact if element hashCode/equality checks are expensive.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access an ArraySet concurrently, and at least
 * one of the threads modifies the set, it <em>must</em> be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the set.
 *
 * If no such object exists, the set should be "wrapped" using the
 * {@link Collections#synchronizedSet Collections.synchronizedSet}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the set: <pre>
 *   Set&lt;...&gt; s = Collections.synchronizedSet(new ArraySet&lt;&gt;(...));</pre>
 *
 * <p>The iterators returned by this class's <tt>iterator</tt> method are
 * <em>fail-fast</em>: if the set is modified at any time after the iterator
 * is created, in any way except through the iterator's own <tt>remove</tt>
 * method, the iterator will throw a {@link ConcurrentModificationException}.
 * Thus, in the face of concurrent modification, the iterator fails quickly
 * and cleanly, rather than risking arbitrary, non-deterministic behavior at
 * an undetermined time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw <tt>ConcurrentModificationException</tt> on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <h2>Implementation Details</h2>
 *
 * <p>This section covers the current implementation; future releases may change
 * some or all of the details.
 *
 * <p>This implementation stores all elements in an insertion-ordered
 * array.  Lookup is done via a hashtable of indices, using
 * <a href="https://en.wikipedia.org/wiki/Double_hashing">double hashing</a>
 * to reduce collisions. As the set grows, the element array grows in the same
 * manner as an <tt>ArrayList</tt>, and the index hashtable is regenerated.
 *
 * <p>Iteration performance is similar to <tt>ArrayList</tt>, though if a lot
 * of elements are deleted, the element array will not be shrunk, meaning
 * iteration performance does not recover once a set has been large.
 *
 * <p>Empty and singleton sets do not allocate any arrays, taking 32B total.
 * Arrays will be allocated once insert is called for a second time, bringing
 * memory use up to 136B until the next resize (11 elements).
 *
 * @param <E> the type of elements maintained by this set
 *
 * @see     Set
 * @see     HashSet
 * @see     LinkedHashSet
 */
public class UnnecessaryReindexingArraySet<E> extends AbstractSet<E> implements Serializable {

  private enum Reserved { NULL }
  private static final int NO_INDEX = -1;
  private static final int DEFAULT_CAPACITY = 10;

  /** Size of the set. */
  private int size = 0;

  /** Next location to insert into {@link #data}, if it is an array. */
  private int head = 0;

  /**
   * Holds the set data. Usually an array, but may be the object itself for a singleton, or null for an empty set.
   *
   * <p>{@link #lookup} will be null when this is not an array.
   */
  private Object data;

  /**
   * Open-addressed hash table of indices into the {@link #data} array. Collisions are resolved with double hashing.
   *
   * <p>Depending on the size of the data array, this could be null (0-1), a byte[] (up to 255) or an int[].
   */
  private Object lookup;

  public static <T> Collector<T, ?, Set<T>> toArraySet() {
    return Collector.of(UnnecessaryReindexingArraySet::new, Set::add, (left, right) -> { left.addAll(right); return left; });
  }

  /**
   * Constructs an empty set with an initial capacity of ten.
   */
  public UnnecessaryReindexingArraySet() {
    data = null;
    lookup = null;
  }

  /**
   * Constructs an empty set with the specified initial capacity.
   *
   * @param  initialCapacity  the initial capacity of the set
   * @return an empty set
   * @throws IllegalArgumentException if the specified initial capacity
   *         is negative
   *
   * @param <E> the type of elements maintained by the set
   */
  public static <E> UnnecessaryReindexingArraySet<E> withInitialCapacity(int initialCapacity) {
    return new UnnecessaryReindexingArraySet<>(initialCapacity);
  }

  @Override
  public void forEach(Consumer<? super E> action) {
    if (lookup == null) {
      @SuppressWarnings("unchecked")
      E item = (E) data;
      if (item != null) {
        action.accept(item == Reserved.NULL ? null : item);
      }
    } else {
      @SuppressWarnings("unchecked")
      E[] objects = (E[]) data;
      for (int i = 0; i < head; ++i) {
        E item = objects[i];
        if (item != null) {
          action.accept(item == Reserved.NULL ? null : item);
        }
      }
    }
  }

  /**
   * Constructs a set containing the elements of the specified
   * collection, in the order they are returned by the collection's
   * iterator. (If an element is duplicated, only the first instance
   * will be stored.)
   *
   * @param elements the collection whose elements are to be placed into this list
   * @throws NullPointerException if the specified collection is null
   */
  public UnnecessaryReindexingArraySet(Collection<? extends E> elements) {
    this(elements.size());
    addAll(elements);
  }

  private UnnecessaryReindexingArraySet(int initialCapacity) {
    checkArgument(initialCapacity >= 0, "initialCapacity must be non-negative");
    data = new Object[Math.max(initialCapacity, DEFAULT_CAPACITY)];
    lookup = newLookupArray();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Iterator<E> iterator() {
    return (lookup == null) ? new SmallIterator() : new LargeIterator();
  }

  @Override
  public Spliterator<E> spliterator() {
    return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
  }

  @Override
  public boolean contains(Object o) {
    Object obj = (o == null) ? Reserved.NULL : o;
    if (lookup == null) {
      return singletonContains(obj);
    } else if (lookup instanceof byte[]) {
      return byteContains(obj, (byte[]) lookup);
    } else if (lookup instanceof short[]) {
      return shortContains(obj, (short[]) lookup);
    } else {
      return intContains(obj, (int[]) lookup);
    }
  }

  private boolean singletonContains(Object obj) {
    return data == obj || (data != null && data.equals(obj));
  }

  private boolean byteContains(Object obj, byte[] lookups) {
    long index = lookup(obj, lookups);
    return (index >= 0);
  }

  private boolean shortContains(Object obj, short[] lookups) {
    long index = lookup(obj, lookups);
    return (index >= 0);
  }

  private boolean intContains(Object obj, int[] lookups) {
    long index = lookup(obj, lookups);
    return (index >= 0);
  }

  @Override
  public boolean add(E e) {
    Object obj = firstNonNull(e, Reserved.NULL);

    if (lookup == null) {
      return singletonAdd(obj);
    }
    // Ensure there is a free cell _before_ looking up index as rehashing invalidates the index.
    ensureFreeCell();
    Object[] objects = (Object[]) data;
    if (lookup instanceof byte[]) {
      return byteAdd(obj, (byte[]) lookup, objects);
    } else if (lookup instanceof short[]) {
      return shortAdd(obj, (short[]) lookup, objects);
    } else {
      return intAdd(obj, (int[]) lookup, objects);
    }
  }

  private boolean singletonAdd(Object obj) {
    Object existing = data;
    if (existing == null) {
      data = obj;
      size++;
      return true;
    } else if (data == obj) {
      return false;
    }
    Object[] objects = new Object[DEFAULT_CAPACITY];
    objects[0] = data;
    data = objects;
    byte[] lookups = (byte[]) newLookupArray();
    lookup = lookups;
    long freeLookupCell = -(lookup(existing, lookups) + 1);
    checkState(freeLookupCell >= 0);
    addLookup((int) freeLookupCell, 0, lookups);
    head = 1;
    return byteAdd(obj, lookups, objects);
  }

  private boolean byteAdd(Object obj, byte[] lookups, Object[] objects) {
    long lookupIndex = lookup(obj, lookups);
    if (lookupIndex >= 0) {
      return false;
    }
    int index = head++;
    objects[index] = obj;
    addLookup((int) -(lookupIndex + 1), index, lookups);

    size++;
    return true;
  }

  private boolean shortAdd(Object obj, short[] lookups, Object[] objects) {
    long lookupIndex = lookup(obj, lookups);
    if (lookupIndex >= 0) {
      return false;
    }
    int index = head++;
    objects[index] = obj;
    addLookup((int) -(lookupIndex + 1), index, lookups);

    size++;
    return true;
  }

  private boolean intAdd(Object obj, int[] lookups, Object[] objects) {
    long lookupIndex = lookup(obj, lookups);
    if (lookupIndex >= 0) {
      return false;
    }
    int index = head++;
    objects[index] = obj;
    addLookup((int) -(lookupIndex + 1), index, lookups);

    size++;
    return true;
  }

  @Override
  public boolean remove(Object o) {
    Object obj = (o == null) ? Reserved.NULL : o;
    if (lookup == null) {
      return singletonRemove(obj);
    } else if (lookup instanceof byte[]) {
      return byteRemove(obj, (byte[]) lookup);
    } else if (lookup instanceof short[]) {
      return shortRemove(obj, (short[]) lookup);
    } else {
      return intRemove(obj, (int[]) lookup);
    }
  }

  private boolean singletonRemove(Object obj) {
    if (data == null || (data != obj && !data.equals(obj))) {
      return false;
    }
    data = null;
    size = 0;
    return true;
  }

  private boolean byteRemove(Object obj, byte[] lookups) {
    long index = lookup(obj, lookups);
    if (index < 0) {
      return false;
    }

    deleteObjectAtIndex((int) index);
    return true;
  }

  private boolean shortRemove(Object obj, short[] lookups) {
    long index = lookup(obj, lookups);
    if (index < 0) {
      return false;
    }

    deleteObjectAtIndex((int) index);
    return true;
  }

  private boolean intRemove(Object obj, int[] lookups) {
    long index = lookup(obj, lookups);
    if (index < 0) {
      return false;
    }

    deleteObjectAtIndex((int) index);
    return true;
  }

  /* Lookup methods */

  private static int log2ceil(int value) {
    return 32 - Integer.numberOfLeadingZeros(value - 1);
  }

  /**
   * If {@code obj} is in the {@code objects} array, returns its index; otherwise, returns
   * {@code (-(probe insertion point) - 1)}, where "probe insertion point" is
   * the index of first free cell in {@code lookups} along the probe sequence for {@code obj}.
   */
  private long lookup(Object obj, byte[] lookups) {
    Object[] objects = (Object[]) data;
    int mask = numLookupCells(lookups) - 1;
    int tombstoneIndex = -1;
    int lookupIndex = obj.hashCode();
    int stride = Integer.reverse(lookupIndex) * 2 + 1;
    lookupIndex &= mask;
    stride &= mask;
    int index;
    while ((index = getLookupAt(lookupIndex, lookups)) != NO_INDEX) {
      Object other = objects[index];
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

  /**
   * If {@code obj} is in the {@code objects} array, returns its index; otherwise, returns
   * {@code (-(probe insertion point) - 1)}, where "probe insertion point" is
   * the index of first free cell in {@code lookups} along the probe sequence for {@code obj}.
   */
  private long lookup(Object obj, short[] lookups) {
    Object[] objects = (Object[]) data;
    int mask = numLookupCells(lookups) - 1;
    int tombstoneIndex = -1;
    int lookupIndex = obj.hashCode();
    int stride = Integer.reverse(lookupIndex) * 2 + 1;
    lookupIndex &= mask;
    stride &= mask;
    int index;
    while ((index = getLookupAt(lookupIndex, lookups)) != NO_INDEX) {
      Object other = objects[index];
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


  /**
   * If {@code obj} is in the {@code objects} array, returns its index; otherwise, returns
   * {@code (-(probe insertion point) - 1)}, where "probe insertion point" is
   * the index of first free cell in {@code lookups} along the probe sequence for {@code obj}.
   */
  private long lookup(Object obj, int[] lookups) {
    Object[] objects = (Object[]) data;
    int mask = numLookupCells(lookups) - 1;
    int tombstoneIndex = -1;
    int lookupIndex = obj.hashCode();
    int stride = Integer.reverse(lookupIndex) * 2 + 1;
    lookupIndex &= mask;
    stride &= mask;
    int index;
    while ((index = getLookupAt(lookupIndex, lookups)) != NO_INDEX) {
      Object other = objects[index];
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

  private Object newLookupArray() {
    // Aim for a power of two with 50% occupancy maximum
    int length = ((Object[]) data).length;
    int numCells = 1 << (log2ceil(length) + 1);
    while (length * 2 > numCells) {
      numCells = numCells * 2;
    }
    if (length < 1L << Byte.SIZE) {
      return new byte[numCells];
    } else if (length < 1L << Short.SIZE) {
      return new short[numCells];
    } else {
      return new int[numCells];
    }
  }

  private static int getLookupAt(int lookupIndex, byte[] lookups) {
    return Byte.toUnsignedInt(lookups[lookupIndex]) - 1;
  }

  private static int getLookupAt(int lookupIndex, short[] lookups) {
    return Short.toUnsignedInt(lookups[lookupIndex]) - 1;
  }

  private static int getLookupAt(int lookupIndex, int[] lookups) {
    return lookups[lookupIndex] - 1;
  }

  private static int numLookupCells(byte[] lookups) {
    return lookups.length;
  }

  private static int numLookupCells(short[] lookups) {
    return lookups.length;
  }

  private static int numLookupCells(int[] lookups) {
    return lookups.length;
  }

  private static void addLookup(int lookupIndex, int index, byte[] lookups) {
    assertState(index != NO_INDEX, "Invalid index");
    assertState(index < 1L << Byte.SIZE, "Index too large");
    lookups[lookupIndex] = (byte) (index + 1);
  }

  private static void addLookup(int lookupIndex, int index, short[] lookups) {
    assertState(index != NO_INDEX, "Invalid index");
    assertState(index < 1L << Short.SIZE, "Index too large");
    lookups[lookupIndex] = (short) (index + 1);
  }

  private static void addLookup(int lookupIndex, int index, int[] lookups) {
    assertState(index != NO_INDEX, "Invalid index");
    lookups[lookupIndex] = index + 1;
  }

  private void clearLookupArray() {
    if (lookup instanceof byte[]) {
      Arrays.fill((byte[]) lookup, (byte) 0);
    } else if (lookup instanceof short[]) {
      Arrays.fill((short[]) lookup, (short) 0);
    } else {
      Arrays.fill((int[]) lookup, 0);
    }
  }

  /* Other internal methods */

  private void ensureFreeCell() {
    Object[] objects = (Object[]) data;
    int oldCapacity = objects.length;
    if (oldCapacity == head) {
      if (size >= minGrowthThreshold(oldCapacity)) {
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        data = Arrays.copyOf(objects, newCapacity);
        lookup = newLookupArray();
      } else {
        clearLookupArray();
      }
      if (lookup instanceof byte[]) {
        compact((byte[]) lookup);
      } else if (lookup instanceof short[]) {
        compact((short[]) lookup);
      } else {
        compact((int[]) lookup);
      }
    }
  }

  private void deleteObjectAtIndex(int index) {
    Object[] objects = (Object[]) data;
    assertState(objects[index] != null, "Cannot delete empty cell");
    assertState(size != 0, "Size is 0 but a cell is not empty");
    objects[index] = null;
    size--;
  }

  private void compact(byte[] lookups) {
    int target = 0;
    Object[] objects = (Object[]) data;
    for (int source = 0; source < objects.length; source++) {
      Object e = objects[source];
      if (e == null) {
        continue;
      }
      if (source != target) {
        objects[target] = e;
      }
      long freeLookupCell = -(lookup(e, lookups) + 1);
      checkState(freeLookupCell >= 0);
      addLookup((int) freeLookupCell, target, lookups);
      target++;
    }
    for (; target < objects.length; target++) {
      objects[target] = null;
    }
    head = size;
  }

  private void compact(short[] lookups) {
    int target = 0;
    Object[] objects = (Object[]) data;
    for (int source = 0; source < objects.length; source++) {
      Object e = objects[source];
      if (e == null) {
        continue;
      }
      if (source != target) {
        objects[target] = e;
      }
      long freeLookupCell = -(lookup(e, lookups) + 1);
      checkState(freeLookupCell >= 0);
      addLookup((int) freeLookupCell, target, lookups);
      target++;
    }
    for (; target < objects.length; target++) {
      objects[target] = null;
    }
    head = size;
  }

  private void compact(int[] lookups) {
    int target = 0;
    Object[] objects = (Object[]) data;
    for (int source = 0; source < objects.length; source++) {
      Object e = objects[source];
      if (e == null) {
        continue;
      }
      if (source != target) {
        objects[target] = e;
      }
      long freeLookupCell = -(lookup(e, lookups) + 1);
      checkState(freeLookupCell >= 0);
      addLookup((int) freeLookupCell, target, lookups);
      target++;
    }
    for (; target < objects.length; target++) {
      objects[target] = null;
    }
    head = size;
  }

  private static int minGrowthThreshold(int capacity) {
    // Grow the objects array if less than a quarter of it is DELETED tombstones when it fills up.
    return capacity * 3 / 4;
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
    if (lookup == null) {
      if (data != null) {
        s.writeObject(data == Reserved.NULL ? null : data);
      }
    } else {
      Object[] objects = (Object[]) data;
      for (int i = 0; i < head; ++i) {
        Object o = objects[i];
        if (o != null) {
          s.writeObject(o == Reserved.NULL ? null : o);
        }
      }
    }
  }

  private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
    size = s.readInt();
    if (size == 0) {
      return;
    } else if (size == 1) {
      data = firstNonNull(s.readObject(), Reserved.NULL);
      return;
    }

    Object[] objects = new Object[Math.max(size, DEFAULT_CAPACITY)];
    data = objects;
    lookup = newLookupArray();
    if (lookup instanceof byte[]) {
      readObjects(s, objects, (byte[]) lookup);
    } else {
      readObjects(s, objects, (int[]) lookup);
    }
  }

  private void readObjects(java.io.ObjectInputStream s, Object[] objects, byte[] lookups)
      throws IOException, ClassNotFoundException, StreamCorruptedException {
    for (head = 0; head < size; head++) {
      Object e = firstNonNull(s.readObject(), Reserved.NULL);
      objects[head] = e;
      long x = lookup(e, lookups);
      long freeLookupCell = -(x + 1);
      if (freeLookupCell < 0) {
        throw new StreamCorruptedException("Duplicate data found in serialized set");
      }
      addLookup((int) freeLookupCell, head, lookups);
    }
  }

  private void readObjects(java.io.ObjectInputStream s, Object[] objects, int[] lookups)
      throws IOException, ClassNotFoundException, StreamCorruptedException {
    for (head = 0; head < size; head++) {
      Object e = firstNonNull(s.readObject(), Reserved.NULL);
      objects[head] = e;
      long x = lookup(e, lookups);
      long freeLookupCell = -(x + 1);
      if (freeLookupCell < 0) {
        throw new StreamCorruptedException("Duplicate data found in serialized set");
      }
      addLookup((int) freeLookupCell, head, lookups);
    }
  }

  /* Iteration */

  private class SmallIterator implements Iterator<E> {
    private int expectedSize;
    private boolean hasNext;

    SmallIterator() {
      expectedSize = size;
      hasNext = data != null;
    }

    @Override
    public boolean hasNext() {
      if (size != expectedSize) {
        throw new ConcurrentModificationException();
      }
      return hasNext;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      hasNext = false;

      @SuppressWarnings("unchecked")
      E o = (E) data;
      if (o == null) {
        throw new ConcurrentModificationException();
      }
      return (o == Reserved.NULL) ? null : o;
    }

    @Override
    public void remove() {
      if (size != expectedSize) {
        throw new ConcurrentModificationException();
      }
      checkState(!hasNext && data != null);
      data = null;
      size = 0;
      expectedSize = 0;
    }
  }

  private class LargeIterator implements Iterator<E> {
    private int expectedSizeAndHead;
    private int index;
    private int nextIndex;

    LargeIterator() {
      expectedSizeAndHead = size ^ (head << 8);
      index = -1;
      nextIndex = 0;
      while (nextIndex < head && ((Object[]) data)[nextIndex] == null) {
        nextIndex++;
      }
    }

    @Override
    public boolean hasNext() {
      if (expectedSizeAndHead != (size ^ (head << 8))) {
        throw new ConcurrentModificationException();
      }
      return nextIndex < head;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      index = nextIndex;
      do {
        nextIndex++;
      } while (nextIndex < head && ((Object[]) data)[nextIndex] == null);

      @SuppressWarnings("unchecked")
      E o = ((E[]) data)[index];
      if (o == null) {
        throw new ConcurrentModificationException();
      }
      return (o == Reserved.NULL) ? null : o;
    }

    @Override
    public void remove() {
      checkState(index != -1);
      if (expectedSizeAndHead != (size ^ (head << 8))) {
        throw new ConcurrentModificationException();
      }
      deleteObjectAtIndex(index);
      index = -1;
      expectedSizeAndHead = size ^ (head << 8);
    }
  }
}
