package org.alicep.collect;

import java.util.function.LongFunction;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

interface ItemFactory<T> extends LongFunction<T> {
  T createItem(long index);

  @Override
  default T apply(long value) {
    return createItem(value);
  }

  HashFunction hashing = Hashing.sha256();
  ItemFactory<Long> longs = index -> hashing.hashLong(index).asLong();
  ItemFactory<String> strings = index -> hashing.hashLong(index).toString();
}