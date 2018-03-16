package org.alicep.collect;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import junit.framework.TestSuite;
import org.alicep.collect.ArrayMapGuavaTests.GuavaTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import java.util.Map;
import java.util.Map.Entry;

@RunWith(Suite.class)
@SuiteClasses(GuavaTests.class)
public class ArrayMapGuavaTests {

  public static class GuavaTests {

    public static TestSuite suite() {
      return MapTestSuiteBuilder
          .using(new TestStringMapGenerator() {
            @Override
            protected Map<String, String> create(Entry<String, String>[] entries) {
              Map<String, String> map = new ArrayMap<>();
              for (Entry<String, String> entry : entries) {
                map.put(entry.getKey(), entry.getValue());
              }
              return map;
            }
          })
          .withFeatures(
              MapFeature.GENERAL_PURPOSE,
              MapFeature.ALLOWS_NULL_KEYS,
              MapFeature.ALLOWS_NULL_VALUES,
              CollectionFeature.REMOVE_OPERATIONS,
              CollectionFeature.SERIALIZABLE,
              CollectionFeature.KNOWN_ORDER,
              CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
              CollectionSize.ANY)
          .named("ArrayMapGuavaTests")
          .createTestSuite();
    }
  }

}
