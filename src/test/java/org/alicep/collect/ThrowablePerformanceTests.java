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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.alicep.benchmark.BenchmarkRunner;
import org.alicep.benchmark.BenchmarkRunner.Benchmark;
import org.alicep.benchmark.BenchmarkRunner.Configuration;
import org.junit.runner.RunWith;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

@RunWith(BenchmarkRunner.class)
public class ThrowablePerformanceTests {

  public enum Mode {
    NOTHING, CREATE_WITHOUT_STACK_TRACE, CREATE, THROW, LAZY, DEPTH, CALLER, EAGER
  }

  @Configuration
  public static List<Mode> CONFIGURATIONS = ImmutableList.copyOf(Mode.values());

  private final Mode mode;

  public ThrowablePerformanceTests(Mode mode) {
    this.mode = mode;
  }

  @Benchmark
  public void makingAnException() {
    if (mode == Mode.NOTHING) {
      return;
    } else if (mode == Mode.CREATE_WITHOUT_STACK_TRACE) {
      new RuntimeException("bobble", null, false, false) {};
      return;
    }
    RuntimeException foo = new RuntimeException("bobble");
    switch (mode) {
      case CREATE:
        return;

      case THROW:
        try {
          throw foo;
        } catch (RuntimeException bar) {
          assertEquals(bar, foo);
        }
        return;

      case LAZY:
        Throwables.lazyStackTrace(foo);
        return;

      case DEPTH:
        Throwables.lazyStackTrace(foo).size();
        return;

      case CALLER:
        assertEquals(Throwables.lazyStackTrace(foo).get(0).getClassName(), ThrowablePerformanceTests.class.getName());
        return;

      case EAGER:
        foo.getStackTrace();
        return;

      default:
        throw new AssertionError();
    }
  }

}
