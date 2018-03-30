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
 * array.  Lookup is done via a compressed hashtable of indices, using
 * <a href="https://en.wikipedia.org/wiki/Double_hashing">double hashing</a>
 * to reduce collisions.  Small sets achieve a higher compression rate,
 * as lookup indexes require fewer bits; a newly-allocated instance
 * needs only 4 bits per bucket to index the default 10-element array.
 * As the set grows, the element array grows in the same manner as a
 * <tt>ArrayList</tt>, and the index hashtable is regenerated.
 *
 * <p>Iteration performance is similar to <tt>ArrayList</tt>, though if a lot
 * of elements are deleted, the element array will not be shrunk, meaning
 * iteration performance does not recover once a set has been large.
 *
 * <p>Memory overhead is a pointer and a half plus a handful of bits per
 * element in the set.  In contrast, <tt>HashSet</tt> allocates approximately
 * five pointers plus 16 bytes per element, while <tt>LinkedHashSet</tt>
 * allocates two more pointers on top of that; an <tt>ArrayList</tt> uses
 * around a pointer and a half.
 *
 * @param <E> the type of elements maintained by this set
 *
 * @see     Set
 * @see     HashSet
 * @see     LinkedHashSet
 */
public class ArraySet<E> extends AbstractSet<E> implements Serializable {

  private enum Reserved { NULL }
  private static final int NO_INDEX = -1;
  private static final int DEFAULT_CAPACITY = 10;
  private static final int STORED_HASH_BITS = 4;
  private static final int STORED_HASH_MASK = (1 << STORED_HASH_BITS) - 1;

  private int size = 0;
  private int modCount = 0;
  private Object[] objects;
  private int head = 0;
  private long[] lookupAndHash;

  public static <T> Collector<T, ?, Set<T>> toArraySet() {
    return Collector.of(ArraySet::new, Set::add, (left, right) -> { left.addAll(right); return left; });
  }

  /**
   * Constructs an empty set with an initial capacity of ten.
   */
  public ArraySet() {
    this(DEFAULT_CAPACITY);
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
  public static <E> ArraySet<E> withInitialCapacity(int initialCapacity) {
    return new ArraySet<>(initialCapacity);
  }

  @Override
  public void forEach(Consumer<? super E> action) {
    for (int i = 0; i < head; ++i) {
      @SuppressWarnings("unchecked")
      E item = (E) objects[i];
      if (item != null) {
        action.accept(item == Reserved.NULL ? null : item);
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
  public ArraySet(Collection<? extends E> elements) {
    this(elements.size());
    addAll(elements);
  }

  private ArraySet(int initialCapacity) {
    checkArgument(initialCapacity >= 0, "initialCapacity must be non-negative");
    objects = new Object[Math.max(initialCapacity, DEFAULT_CAPACITY)];
    lookupAndHash = newLookupArray();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Iterator<E> iterator() {
    return new IteratorImpl();
  }

  @Override
  public Spliterator<E> spliterator() {
    return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
  }

  @Override
  public boolean contains(Object o) {
    Object comparisonObject = (o == null) ? Reserved.NULL : o;
    long index = lookup(comparisonObject);
    return (index >= 0);
  }

  @Override
  public boolean add(E e) {
    Object insertionObject = firstNonNull(e, Reserved.NULL);

    // Ensure there is a free cell _before_ looking up index as rehashing invalidates the index.
    ensureFreeCell();
    int hashCode = insertionObject.hashCode();
    long lookupIndex = lookup(insertionObject, hashCode);
    if (lookupIndex >= 0) {
      return false;
    }
    int index = head++;
    objects[index] = insertionObject;
    addLookup((int) -(lookupIndex + 1), index, hashCode);

    size++;
    modCount++;
    return true;
  }

  @Override
  public boolean remove(Object o) {
    long index = lookup((o == null) ? Reserved.NULL : o);
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

  private int lookupEntryBits() {
    int size = objects.length;
    // 4 divides 64 into 16
    // 5 divides 64 into 12
    // 6 divides 64 into 10
    // 7 divides 64 into 9
    // 8 divides 64 into 8
    // 9 divides 64 into 7
    // 10 divides 64 into 6
    // 12 divides 64 into 5
    // 16 divides 64 into 4
    // 21 divides 64 into 3
    // 32 divides 64 into 2
    if (size <= 1<<4) {
      return 4;
    } else if (size <= 1<<5) {
      return 5;
    } else if (size <= 1<<6) {
      return 6;
    } else if (size <= 1<<7) {
      return 7;
    } else if (size <= 1<<8) {
      return 8;
    } else if (size <= 1<<9) {
      return 9;
    } else if (size <= 1<<10) {
      return 10;
    } else if (size <= 1<<12) {
      return 12;
    } else if (size <= 1<<16) {
      return 16;
    } else if (size <= 1<<21) {
      return 21;
    } else if (size <= 1<<32) {
      return 32;
    } else {
      return 64;
    }
  }

  private int lookupEntriesPerLong() {
    return Long.SIZE / (lookupEntryBits() + STORED_HASH_BITS);
  }

  private long[] newLookupArray() {
    // Aim for a power of two with 70% occupancy maximum
    int numCells = 1 << log2ceil(objects.length);
    while (objects.length + (objects.length >> 1) - (objects.length >> 4) > numCells) {
      numCells = numCells * 2;
    }
    int cellsPerLong = lookupEntriesPerLong();
    long[] lookup = new long[1 + (numCells - 1) / cellsPerLong];
    Arrays.fill(lookup, -1);
    return lookup;
  }

  private void addLookup(int lookupIndex, int index, int hash) {
    assertState(index != NO_INDEX, "Invalid index");
    long bits = index << STORED_HASH_BITS | hashNibble(hash);
    if (lookupEntryBits() + STORED_HASH_BITS < Long.SIZE) {
      addLookupNibble(lookupIndex, bits);
    } else {
      lookupAndHash[lookupIndex] = bits;
    }
  }

  private long lookupAndHashMask() {
    return (1 << (lookupEntryBits() + STORED_HASH_BITS)) - 1;
  }

  private long hashNibble(int hash) {
    return (hash >> log2ceil(lookupAndHash.length)) & STORED_HASH_MASK;
  }

  private void addLookupNibble(int lookupIndex, long bits) {
    long word = lookupAndHash[lookupIndex / lookupEntriesPerLong()];
    int shift = (lookupEntryBits() + STORED_HASH_BITS) * (lookupIndex % lookupEntriesPerLong());
    word &= ~(lookupAndHashMask() << shift);
    word |= (bits & lookupAndHashMask()) << shift;
    lookupAndHash[lookupIndex / lookupEntriesPerLong()] = word;
  }

  /**
   * If {@code obj} is in the {@code objects} array, returns its index; otherwise, returns
   * {@code (-(probe insertion point) - 1)}, where "probe insertion point" is
   * the index of first free cell in {@code lookup} along the probe sequence for {@code obj}.
   */
  private long lookup(Object obj) {
    return lookup(obj, obj.hashCode());
  }

  private long lookup(Object obj, int hashCode) {
    int mask = numLookupCells() - 1;
    int tombstoneIndex = -1;
    int lookupIndex = hashCode;
    int stride = Integer.reverse(lookupIndex) * 2 + 1;
    lookupIndex &= mask;
    stride &= mask;
    int indexAndHash;
    while ((indexAndHash = getLookupAndHashAt(lookupIndex)) != NO_INDEX) {
      if (hashNibble(hashCode) == (indexAndHash & STORED_HASH_MASK)) {
        Object other = objects[indexAndHash >> STORED_HASH_BITS];
        if (other == null) {
          if (tombstoneIndex == -1) {
            tombstoneIndex = lookupIndex;
          }
        } else if (other.equals(obj)) {
          return indexAndHash >> STORED_HASH_BITS;
        }
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
    return Integer.highestOneBit(lookupAndHash.length * lookupEntriesPerLong());
  }

  private int getLookupAndHashAt(int lookupIndex) {
    long word = lookupAndHash[lookupIndex / lookupEntriesPerLong()];
    int shift = (lookupEntryBits() + STORED_HASH_BITS) * (lookupIndex % lookupEntriesPerLong());
    int value = (int) ((word >> shift) & lookupAndHashMask());
    return (value == (NO_INDEX & lookupAndHashMask())) ? NO_INDEX : value;
  }

  private void clearLookupArray() {
    Arrays.fill(lookupAndHash, -1);
  }

  /* Other internal methods */

  private void ensureFreeCell() {
    if (objects.length == head) {
      if (size >= minGrowthThreshold()) {
        int newSize = objects.length + (objects.length >> 1);
        objects = Arrays.copyOf(objects, newSize);
        lookupAndHash = null;
      }
      compact();
    }
  }

  private void deleteObjectAtIndex(int index) {
    assertState(objects[index] != null, "Cannot delete empty cell");
    assertState(size != 0, "Size is 0 but a cell is not empty");
    objects[index] = null;
    size--;
    modCount++;
  }

  private void compact() {
    if (lookupAndHash == null) {
      lookupAndHash = newLookupArray();
    } else {
      clearLookupArray();
    }
    int target = 0;
    for (int source = 0; source < objects.length; source++) {
      Object e = objects[source];
      if (e == null) {
        continue;
      }
      if (source != target) {
        objects[target] = e;
      }
      long freeLookupCell = -(lookup(e) + 1);
      checkState(freeLookupCell >= 0);
      addLookup((int) freeLookupCell, target, e.hashCode());
      target++;
    }
    for (; target < objects.length; target++) {
      objects[target] = null;
    }
    head = size;
  }

  private int minGrowthThreshold() {
    // Grow the objects array if less than a quarter of it is DELETED tombstones when it fills up.
    return objects.length * 3 / 4;
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
    for (int i = 0; i < head; ++i) {
      Object o = objects[i];
      if (o != null) {
        s.writeObject(o == Reserved.NULL ? null : o);
      }
    }
  }

  private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
    size = s.readInt();
    objects = new Object[Math.max(size, DEFAULT_CAPACITY)];
    lookupAndHash = newLookupArray();
    clearLookupArray();
    for (head = 0; head < size; head++) {
      Object e = firstNonNull(s.readObject(), Reserved.NULL);
      objects[head] = e;
      int hashCode = e.hashCode();
      long x = lookup(e, hashCode);
      long freeLookupCell = -(x + 1);
      if (freeLookupCell < 0) {
        throw new StreamCorruptedException("Duplicate data found in serialized set");
      }
      addLookup((int) freeLookupCell, head, hashCode);
    }
  }

  /* Iteration */

  private class IteratorImpl implements Iterator<E> {
    private int expectedModCount;
    private int index;
    private int nextIndex;

    IteratorImpl() {
      expectedModCount = modCount;
      index = -1;
      nextIndex = 0;
      while (nextIndex < head && objects[nextIndex] == null) {
        nextIndex++;
      }
    }

    @Override
    public boolean hasNext() {
      if (modCount != expectedModCount) {
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
      } while (nextIndex < head && objects[nextIndex] == null);

      @SuppressWarnings("unchecked")
      E o = (E) objects[index];
      if (o == null) {
        throw new ConcurrentModificationException();
      }
      return (o == Reserved.NULL) ? null : o;
    }

    @Override
    public void remove() {
      checkState(index != -1);
      if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
      deleteObjectAtIndex(index);
      index = -1;
      expectedModCount = modCount;
    }
  }
}
