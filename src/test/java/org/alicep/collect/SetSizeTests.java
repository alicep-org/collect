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
package org.alicep.collect;

import static org.alicep.collect.benchmark.Bytes.bytes;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.alicep.collect.benchmark.Bytes;
import org.alicep.collect.benchmark.MemGauge;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SetSizeTests {

  @Parameters(name = "{0} elements")
  public static List<Integer> sizes() {
    List<Integer> sizes = new ArrayList<>();
    sizes.add(0);
    sizes.add(1);
    sizes.add(2);
    sizes.add(3);
    for (int saturated = 10; saturated < 1_000; saturated = saturated + (saturated >> 1)) {
      sizes.add(saturated);
      sizes.add(saturated + 1);
    }
    sizes.add(1_000);
    sizes.add(10_000);
    sizes.add(100_000);
    return sizes;
  }

  @Parameter
  public int size;

  /**
   * Validate the memory numbers contained in README.txt.
   */
  @Test
  public void checkMemoryUsed() {
    String[] values = new String[size];
    Arrays.setAll(values, i -> ItemFactory.strings.createItem(i));
    Bytes bytes = MemGauge.measureMemoryUsage($ -> {
      Set<String> set = new ArraySet<>();
      for (String value : values) {
        set.add(value);
      }
      return set;
    });
    assertEquals(expectedMemoryUsed(size), bytes);
  }

  private static Bytes expectedMemoryUsed(long elements) {
    if (elements <= 1) return bytes(32);
    int dataSize = 10;
    while (dataSize < elements) {
      dataSize += dataSize >> 1;
    }
    int indices = 16;
    while (indices < dataSize * 2) {
      indices *= 2;
    }
    int bytesPerIndex = 1;
    while (dataSize >= (1L << (bytesPerIndex * Byte.SIZE))) {
      bytesPerIndex *= 2;
    }
    return bytes(64 + roundUpToAMultipleOf(2, dataSize) * 4 + roundUpToAMultipleOf(4, indices * bytesPerIndex));
  }

  private static int roundUpToAMultipleOf(int factor, int value) {
    return (1 + (value - 1) / factor) * factor;
  }
}
