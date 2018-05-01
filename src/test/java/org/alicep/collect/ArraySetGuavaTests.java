package org.alicep.collect;

import java.util.Arrays;
import java.util.Set;

import org.alicep.collect.ArraySetGuavaTests.GuavaTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.google.common.collect.testing.SetTestSuiteBuilder;
import com.google.common.collect.testing.TestStringSetGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.SetFeature;

import junit.framework.TestSuite;

@RunWith(Suite.class)
@SuiteClasses(GuavaTests.class)
public class ArraySetGuavaTests {

  public static class GuavaTests {
    public static TestSuite suite() {
      return SetTestSuiteBuilder
          .using(new TestStringSetGenerator() {
            @Override
            protected Set<String> create(String[] elements) {
              return new ArraySet<>(Arrays.asList(elements));
            }
          })
          .withFeatures(
              SetFeature.GENERAL_PURPOSE,
              CollectionFeature.SERIALIZABLE,
              CollectionFeature.ALLOWS_NULL_VALUES,
              CollectionFeature.KNOWN_ORDER,
              CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
              CollectionSize.ANY)
          .named("ArraySetGuavaTests")
          .createTestSuite();
    }
  }
}
