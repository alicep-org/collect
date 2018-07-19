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

import static java.lang.Runtime.getRuntime;
import static java.util.Arrays.stream;
import static org.alicep.benchmark.Bytes.bytes;
import static org.alicep.benchmark.MemoryAssertions.assertThatRunning;
import static org.alicep.collect.ItemFactory.strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.alicep.benchmark.Bytes;
import org.alicep.benchmark.MemGauge;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.koloboke.collect.impl.hash.LHashObjSetFactoryImpl;

@RunWith(Parameterized.class)
public class SetSizeTests {

  @Parameters(name = "{0} elements")
  public static Set<Integer> sizes() {
    Set<Integer> sizes = new TreeSet<>();
    sizes.add(0);
    sizes.add(1);
    sizes.add(2);
    sizes.add(3);
    for (int saturated = 10; saturated < 1_000; saturated = saturated + (saturated >> 1)) {
      sizes.add(saturated);
      sizes.add(saturated + 1);
    }
    for (int powerOfTwo = 8; powerOfTwo < 1_000; powerOfTwo *= 2) {
      sizes.add(powerOfTwo);
      if (powerOfTwo == 256) {
        // Transition from byte[] to short[] happens at 255
        sizes.add(powerOfTwo - 1);
      } else {
        sizes.add(powerOfTwo + 1);
      }
    }
    sizes.add(1_000);
    sizes.add(10_000);
    sizes.add(100_000);
    return sizes;
  }

  private static final TreeMap<Integer, Bytes> sizeTally = new TreeMap<>();
  private static String[] values = new String[0];

  @BeforeClass
  public static void beforeClass() {
    values = new String[sizes().stream().mapToInt(i -> i).max().getAsInt()];
    Arrays.setAll(values, strings::createItem);
    sizeTally.clear();
  }

  @AfterClass
  public static void printTally() throws InterruptedException {
    if (sizeTally.isEmpty() || inGradle()) {
      return;
    }
    System.out.println("| Collection size | ArraySet |    HashSet    | LinkedHashSet | ArrayList |   [ObjSet]    |");
    System.out.println("| --------------- | -------- | ------------- | ------------- | --------- | ------------- |");
    int lastChange = -1;
    int lastSize = -1;
    Bytes lastBytes = null;
    for (int size : sizeTally.keySet()) {
      Bytes bytes = sizeTally.get(size);
      if (lastChange == -1) {
        lastChange = size;
      } else if (!bytes.equals(lastBytes)) {
        printRow(lastChange, lastSize, lastBytes);
        lastChange = size;
      }
      lastSize = size;
      lastBytes = bytes;
    }
    printRow(lastChange, lastSize, lastBytes);
  }

  private static boolean inGradle() {
    return System.getProperties().keySet().stream().anyMatch(k -> k.toString().startsWith("org.gradle."));
  }

  private static void printRow(int fromSize, int toSize, Bytes bytes) throws InterruptedException {
    System.out.print("| ");
    StringBuilder arraySize = new StringBuilder();
    arraySize.append(prettyCount(fromSize));
    if (fromSize != toSize) {
      arraySize.append("–" + prettyCount(toSize));
    }
    tabulate(arraySize, 15);
    System.out.print(" | ");
    tabulate(bytes, 8);
    System.out.print(" | ");
    tabulate(memoryUsage(HashSet::new, fromSize, toSize), 13);
    System.out.print(" | ");
    tabulate(memoryUsage(LinkedHashSet::new, fromSize, toSize), 13);
    System.out.print(" | ");
    tabulate(memoryUsage(ArrayList::new, fromSize, toSize), 9);
    System.out.print(" | ");
    tabulate(memoryUsage(new LHashObjSetFactoryImpl<String>()::newUpdatableSet, fromSize, toSize), 13);
    System.out.println(" |");
  }

  private static String prettyCount(int size) {
    if (size > 0 && size % 1000 == 0) {
      return (size / 1000) + "k";
    }
    return Integer.toString(size);
  }

  private static String memoryUsage(Supplier<Collection<String>> factory, int fromSize, int toSize)
      throws InterruptedException {
    Bytes fromBytes = getMemoryUsage(factory, fromSize);
    Bytes toBytes = getMemoryUsage(factory, toSize);
    StringBuilder sizeString = new StringBuilder();
    sizeString.append(fromBytes);
    if (!fromBytes.equals(toBytes)) {
      sizeString.append("–").append(toBytes);
    }
    return sizeString.toString();
  }

  private static void tabulate(Object value, int colSize) {
    String valueString = value.toString();
    int padding = colSize - valueString.length();
    for (int i = padding / 2; i < padding; ++i) {
      System.out.print(" ");
    }
    System.out.print(value);
    for (int i = 0; i < padding / 2; ++i) {
      System.out.print(" ");
    }
  }

  @Parameter
  public int size;

  /**
   * Validate the memory numbers contained in README.txt.
   */
  @Test
  public void checkMemoryUsed() {
    assertThatRunning(() -> {
      Collection<String> set = new BucketPairSpikeArraySet<>();
      stream(values).limit(size).forEach(set::add);
      return set;
    }).returnsObjectConsuming(expectedMemoryUsed(size));
    sizeTally.put(size, expectedMemoryUsed(size));
  }

  private static Bytes getMemoryUsage(Supplier<Collection<String>> factory, int size)
      throws InterruptedException {
    Bytes bytes = MemGauge.objectSize(() -> {
      Collection<String> set = factory.get();
      for (int i = 0; i < size; ++i) {
        set.add(values[i]);
      }
      return set;
    });
    return bytes;
  }

  private static final int HEADER_BYTES = 16;

  private static Bytes expectedMemoryUsed(int elements) {
    int sizeOfBaseObject = HEADER_BYTES + 2 * Integer.BYTES + 2 * referenceSize();
    if (elements <= 1) return bytes(sizeOfBaseObject);
    int dataSize = 10;
    while (dataSize < elements) {
      dataSize += dataSize >> 1;
    }
    int indices = Math.max(Integer.highestOneBit(elements - 1) << 2, 16);
    if (elements == (1L << Byte.SIZE) || elements == (1L << Short.SIZE)) {
      // Grows early at these sizes as -1 is a reserved constant in the offset array
      indices *= 2;
    }
    int bytesPerIndex = 1;
    while (elements > (1L << (bytesPerIndex * Byte.SIZE)) - 1) {
      bytesPerIndex *= 2;
    }
    int sizeOfObjectArray = arrayHeaderBytes() + roundUpToAMultipleOf(Long.BYTES, dataSize * referenceSize());
    int sizeOfIndexArray = arrayHeaderBytes() + roundUpToAMultipleOf(Long.BYTES, indices * bytesPerIndex);
    return bytes(sizeOfBaseObject + sizeOfObjectArray + sizeOfIndexArray);
  }

  private static int roundUpToAMultipleOf(int factor, int value) {
    return (Math.floorDiv((value - 1), factor) + 1) * factor;
  }

  private static int referenceSize() {
    return is64BitJvm() ? Long.BYTES : Integer.BYTES;
  }

  private static int arrayHeaderBytes() {
    return is64BitJvm() ? 24 : 16;
  }

  private static boolean is64BitJvm() {
    return getRuntime().maxMemory() >= 4L * Integer.MAX_VALUE;
  }
}
