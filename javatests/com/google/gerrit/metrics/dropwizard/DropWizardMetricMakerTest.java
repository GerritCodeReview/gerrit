// Copyright (C) 2018 The Android Open Source Project
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricsReservoirConfig;
import com.google.gerrit.metrics.ReservoirType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DropWizardMetricMakerTest {

  @Mock MetricsReservoirConfig reservoirConfigMock;

  MetricRegistry registry;

  DropWizardMetricMaker metrics;

  @Before
  public void setupMocks() {
    registry = new MetricRegistry();
    metrics = new DropWizardMetricMaker(registry, reservoirConfigMock);
  }

  @Test
  public void shouldSanitizeUnwantedChars() throws Exception {
    assertThat(metrics.sanitizeMetricName("very+confusing$long#metric@net/name^1"))
        .isEqualTo("very_confusing_long_metric_net/name_1");
    assertThat(metrics.sanitizeMetricName("/metric/submetric")).isEqualTo("_metric/submetric");
  }

  @Test
  public void shouldReduceConsecutiveSlashesToOne() throws Exception {
    assertThat(metrics.sanitizeMetricName("/metric//submetric1///submetric2/submetric3"))
        .isEqualTo("_metric/submetric1/submetric2/submetric3");
  }

  @Test
  public void shouldNotFinishWithSlash() throws Exception {
    assertThat(metrics.sanitizeMetricName("metric/")).isEqualTo("metric");
    assertThat(metrics.sanitizeMetricName("metric//")).isEqualTo("metric");
    assertThat(metrics.sanitizeMetricName("metric/submetric/")).isEqualTo("metric/submetric");
  }

  @Test
  public void shouldRequestForReservoirForNewTimer() throws Exception {
    when(reservoirConfigMock.reservoirType()).thenReturn(ReservoirType.ExponentiallyDecaying);

    metrics.newTimer(
        "foo",
        new Description("foo description").setCumulative().setUnit(Description.Units.MILLISECONDS));

    verify(reservoirConfigMock).reservoirType();
  }
}
