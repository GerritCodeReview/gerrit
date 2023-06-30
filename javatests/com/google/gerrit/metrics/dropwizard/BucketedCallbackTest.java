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
import com.google.gerrit.metrics.MetricsReservoirConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class BucketedCallbackTest {

  @Mock MetricsReservoirConfig reservoirConfigMock;

  MetricRegistry registry;

  DropWizardMetricMaker metrics;

  @Before
  public void setupMocks() {
    registry = new MetricRegistry();
    metrics = new DropWizardMetricMaker(registry, reservoirConfigMock);
  }

  @Test
  public void shouldRegisterMetricWithNewKey() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();

    bc.getOrCreate("foo");
    assertThat(registry.getNames()).containsExactly("name/foo");

    bc.getOrCreate("bar");
    assertThat(registry.getNames()).containsExactly("name/foo", "name/bar");
  }

  @Test
  public void shouldNotReregisterPreviouslyRegisteredMetric() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();
    bc.getOrCreate("foo");
    bc.getOrCreate("foo");
    assertThat(registry.getNames()).containsExactly("name/foo");
  }

  @Test
  public void shouldStoreKeyValueInCellsAndRegisterSubmetricName() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();
    bc.getOrCreate("setToFoo");
    assertThat(bc.getCells().keySet()).containsExactly("setToFoo");
    assertThat(registry.getNames()).containsExactly("name/foo");
  }

  @Test
  public void shouldErrorIfKeyIsDifferentButNameClashes() {
    BucketedCallback<Long> bc = new CallbackMetricTestImpl();
    bc.getOrCreate("foo");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> bc.getOrCreate("setToFoo"));
    assertThat(exception.getMessage())
        .isEqualTo("Key [setToFoo] maps to an already existing submetric [name/foo]");

    assertThat(bc.getCells().keySet()).containsExactly("foo");
    assertThat(registry.getNames()).containsExactly("name/foo");
  }

  private class CallbackMetricTestImpl extends BucketedCallback<Long> {

    CallbackMetricTestImpl() {
      super(metrics, registry, "name", Long.class, new Description("description"));
    }

    @Override
    String name(Object key) {
      if (key.equals("setToFoo")) {
        return "foo";
      } else {
        return key.toString();
      }
    }
  }
}
