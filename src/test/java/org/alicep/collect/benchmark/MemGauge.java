/*
 * Copyright 2018 Palantir Technologies, Inc.
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
package org.alicep.collect.benchmark;

import static org.alicep.collect.benchmark.Bytes.bytes;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.function.IntFunction;

import org.junit.Test;

public class MemGauge {

  @Test
  public void testLong() {
    assertEquals(bytes(24), measureMemoryUsage(Long::new));
  }

  @Test
  public void testString() {
    assertEquals(bytes(56), measureMemoryUsage(i -> String.format("%08x", i)));
  }

  @Test
  public void testByteArray() {
    // Round up to a multiple of 4 and add 16 bits of header (object header + size)
    assertEquals(bytes(24), measureMemoryUsage(i -> new byte[5]));
  }

  @Test
  public void testEvenSizedPointerArray() {
    // 4 bytes per pointer and a 16-bit header (object header + size)
    assertEquals(bytes(56), measureMemoryUsage(i -> new String[10]));
  }

  @Test
  public void testOddSizedPointerArray() {
    // Round up to a multiple of 2, 4 bytes per pointer and a 16-bit header (object header + size)
    assertEquals(bytes(80), measureMemoryUsage(i -> new String[15]));
  }

  /**
   * Returns the memory used by an object returned by {@code supplier}.
   */
  public static Bytes measureMemoryUsage(IntFunction<?> supplier) {
    MemoryAllocationMonitor monitor = MemoryAllocationMonitor.get();
    if (monitor.memoryUsed() == -1) {
      throw new AssertionError("Cannot measure memory on this JVM");
    }

    // Measure GCs with and without the object in question in.
    // Sometimes a GC will be unusually low; often it will be unusually high.
    // Keep going until the 1st quartile equals the median for both sets of measurements.
    int head = 0;
    int tail = 7;
    long[] without = new long[tail];
    long[] with = new long[tail];
    long medianWith;
    long medianWithout;
    do {
      for (int i = head; i < tail; ++i) {
        @SuppressWarnings("unused")
        Object obj = supplier.apply(i);
        long memory1 = monitor.memoryUsed();
        long memory2 = monitor.memoryUsed();
        obj = null;
        long memory3 = monitor.memoryUsed();
        without[i] = memory2 - memory1;
        with[i] = memory3 - memory2;
      }
      Arrays.sort(without);
      Arrays.sort(with);
      long q1Without = without[tail / 4];
      medianWithout = without[tail / 2];
      long q1With = with[tail / 4];
      medianWith = with[tail / 2];
      if (q1Without == medianWithout && q1With == medianWith) {
        break;
      }
      head = tail;
      tail = tail + 4;
      without = Arrays.copyOf(without, tail);
      with = Arrays.copyOf(with, tail);
    } while (true);

    // Take the difference between the medians
    return bytes(medianWith - medianWithout);
  }
}
