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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

public class Bytes implements Comparable<Bytes> {

  public static Bytes bytes(long bytes) {
    checkArgument(bytes >= 0);
    return new Bytes(bytes);
  }

  public static Bytes kilobytes(long kilobytes) {
    checkArgument(kilobytes >= 0);
    return new Bytes(kilobytes * 1_000);
  }

  public static Bytes megabytes(long megabytes) {
    checkArgument(megabytes >= 0);
    return new Bytes(megabytes * 1_000_000);
  }

  public static Bytes gigabytes(long gigabytes) {
    checkArgument(gigabytes >= 0);
    return new Bytes(gigabytes * 1_000_000_000);
  }

  private final long bytes;

  private Bytes(long bytes) {
    this.bytes = bytes;
  }

  public long asLong() {
    return bytes;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Bytes)) {
      return false;
    }
    return bytes == ((Bytes) obj).bytes;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(bytes);
  }

  @Override
  public int compareTo(Bytes o) {
    return Long.compare(bytes, o.bytes);
  }

  private static final Map<Integer, String> SCALES = ImmutableMap.<Integer, String>builder()
      .put(0, "")
      .put(1, "k")
      .put(2, "M")
      .put(3, "G")
      .put(4, "T")
      .put(5, "P")
      .put(6, "E")
      .build();

  @Override
  public String toString() {
    checkArgument(bytes >= 0);
    if (bytes < 995) return bytes + "B";
    double scaled = bytes;
    int scale = 0;
    while (scaled >= 999) {
      scaled /= 1000;
      scale += 1;
    }
    String significand;
    if (scaled < 9.995) {
      significand = String.format("%.2f", scaled);
    } else if (scaled < 99.95) {
      significand = String.format("%.1f", scaled);
    } else {
      significand = Long.toString(Math.round(scaled * 10) / 10);
    }

    return String.format("%s%sB", significand, SCALES.get(scale));
  }
}
