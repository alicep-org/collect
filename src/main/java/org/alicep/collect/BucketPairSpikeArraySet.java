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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Iterator;

/**
 * This spike combines adjacent buckets into two-entry pairs, using the spare bits when one entry is empty to store
 * a fingerprint of the other entry's hashcode. An overflow bit avoid unnecessary probe sequence scans.
 *
 * <p>Since we target a 50% residency, this means over 70% of misses will perform a single array lookup, and most of the
 * remainder will perform three array reads and two equality checks.
 */
public class BucketPairSpikeArraySet<E> extends AbstractSet<E> implements Serializable {

  /** If this bit is set, the cell contains two entries. */
  private static final short FULL = (short) 0x8000;

  /** If this bit and {@link #FULL} are set, an entry has overflowed to another bucket. */
  private static final short OVERFLOWED = (short) 0x4000;

  /** If {@link #FULL} is not set, this is the bitshift of the entry's partial hashcode. */
  private static final short HASHCODE_SHIFT = 7;

  /** If {@link #FULL} is not set, this masks the entry's partial hashcode. */
  private static final short HASHCODE_MASK = (short) (0xFF << HASHCODE_SHIFT);

  /** This masks indices in the cell. */
  private static final short INDEX_MASK = (short) 0x007F;

  /** If {@link #FULL} is set, this is the amount the second index is shifted. */
  private static final short INDEX_SHIFT = 7;

  @SuppressWarnings("unchecked")
  private final E[] objects = (E[]) new Object[49];
  private int head = 0;
  private final short[] lookup = new short[64];

  @Override
  public boolean contains(Object o) {
    int hashCode = o.hashCode();
    int cellIndex = hashCode & (lookup.length - 1);
    short cellState = lookup[cellIndex];
    int i = 0;
    do {
      if ((cellState & FULL) == 0) {
        if ((cellState & HASHCODE_MASK) != (hashCode & HASHCODE_MASK)) {
          return false;
        }
        int index = cellState & INDEX_MASK;
        if (index == 0) {
          return false;
        }
        E other = objects[index - 1];
        return (o == other || o.equals(other));
      } else {
        E other1 = objects[(cellState & INDEX_MASK) - 1];
        if (o == other1 || o.equals(other1)) {
          return true;
        }
        E other2 = objects[((cellState >> INDEX_SHIFT) & INDEX_MASK) - 1];
        if (o == other2 || o.equals(other2)) {
          return true;
        }
      }
      if ((cellState & OVERFLOWED) == 0) {
        return false;
      }
      cellIndex = (cellIndex + (++i)) & (lookup.length - 1);
      cellState = lookup[cellIndex];
    } while (i < lookup.length);
    return false;
  }

  @Override
  public boolean add(E e) {
    checkNotNull(e);
    if (contains(e)) {
      return false;
    }
    checkState(head < objects.length);
    objects[head] = e;
    addLookup(head, e.hashCode());
    head++;
    return true;
  }

  private void addLookup(int idx, int hashCode) {
    checkArgument(idx >= 0 && idx <= INDEX_MASK);
    int cellIndex = hashCode & (lookup.length - 1);
    short cellState = lookup[cellIndex];
    int i = 0;
    while ((cellState & FULL) != 0) {
      if ((cellState & OVERFLOWED) == 0) {
        lookup[cellIndex] = (short) (cellState | OVERFLOWED);
      }
      checkState(i <= lookup.length, "Set full");
      cellIndex = (cellIndex + (++i)) & (lookup.length - 1);
      cellState = lookup[cellIndex];
    } while ((cellState & FULL) != 0);
    short newCellState;
    if ((cellState & INDEX_MASK) != 0) {
      // One entry in the cell already
      short originalIndex = (short) (cellState & INDEX_MASK);
      newCellState = (short) (FULL | ((idx + 1) << INDEX_SHIFT) | originalIndex);
    } else {
      // Empty cell
      newCellState = (short) ((hashCode & HASHCODE_MASK) | (idx + 1));
    }
    lookup[cellIndex] = newCellState;
  }

  @Override
  public Iterator<E> iterator() {
    return new Iterator<E>() {

      private int i = 0;

      @Override
      public boolean hasNext() {
        return i < head;
      }

      @Override
      public E next() {
        checkState(i < head);
        return objects[i++];
      }
    };
  }

  @Override
  public int size() {
    return head;
  }
}
