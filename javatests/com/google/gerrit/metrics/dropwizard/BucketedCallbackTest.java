// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.metrics.dropwizard;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.metrics.Description;
import org.junit.Before;
import org.junit.Test;

public class BucketedCallbackTest {

  private MetricRegistry registry;

  private DropWizardMetricMaker metrics;

  private static final String CODE_NAME = "name";
  private static final String KEY_NAME = "foo";
  private static final String OTHER_KEY_NAME = "bar";
  private static final String COLLIDING_KEY_NAME1 = "foo1";
  private static final String COLLIDING_KEY_NAME2 = "foo2";
  private static final String COLLIDING_SUBMETRIC_NAME = "foocollision";

  private String metricName(String fieldValues) {
    return CODE_NAME + "/" + fieldValues;
  }

  @Before
  public void setup() {
    registry = new MetricRegistry();
    metrics = new DropWizardMetricMaker(registry, null);
  }

  @Test
  public void shouldRegisterMetricWithNewKey() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();

    bc.getOrCreate(KEY_NAME);
    assertThat(registry.getNames()).containsExactly(metricName(KEY_NAME));

    bc.getOrCreate(OTHER_KEY_NAME);
    assertThat(registry.getNames())
        .containsExactly(metricName(KEY_NAME), metricName(OTHER_KEY_NAME));
  }

  @Test
  public void shouldNotReRegisterPreviouslyRegisteredMetric() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();
    bc.getOrCreate(KEY_NAME);
    bc.getOrCreate(KEY_NAME);
    assertThat(registry.getNames()).containsExactly(metricName(KEY_NAME));
  }

  @Test
  public void shouldStoreKeyValueInCellsAndRegisterSubmetricName() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();
    bc.getOrCreate(COLLIDING_KEY_NAME1);
    assertThat(bc.getCells().keySet()).containsExactly(COLLIDING_KEY_NAME1);
    assertThat(registry.getNames()).containsExactly(metricName(COLLIDING_SUBMETRIC_NAME));
  }

  @Test
  public void shouldErrorIfKeyIsDifferentButNameCollides() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();
    bc.getOrCreate(COLLIDING_KEY_NAME1);

    assertThrows(IllegalArgumentException.class, () -> bc.getOrCreate(COLLIDING_KEY_NAME2));
    assertThat(bc.getCells().keySet()).containsExactly(COLLIDING_KEY_NAME1);
    assertThat(registry.getNames()).containsExactly(metricName(COLLIDING_SUBMETRIC_NAME));
  }

  private class CallbackMetricTestImpl extends BucketedCallback<Long> {

    CallbackMetricTestImpl() {
      super(metrics, registry, CODE_NAME, Long.class, new Description("description"));
    }

    @Override
    String name(Object key) {
      if (key.equals(COLLIDING_KEY_NAME1) || key.equals(COLLIDING_KEY_NAME2)) {
        return COLLIDING_SUBMETRIC_NAME;
      } else {
        return key.toString();
      }
    }
  }
}
