package org.alicep.collect;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

interface ItemFactory<T> {
  T createItem(long index);

  HashFunction hashing = Hashing.sha256();
  ItemFactory<Long> longs = index -> hashing.hashLong(index).asLong();
  ItemFactory<String> strings = index -> hashing.hashLong(index).toString();
}