package org.alicep.collect;

import java.util.stream.LongStream;

class LongStreams {

  public static LongStream longs(long start, long end) {
    return LongStream.iterate(start, i -> i + 1).limit(end);
  }

  private LongStreams() { }
}
