// Copyright (C) 2022 The Android Open Source Project
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
import static org.mockito.Mockito.when;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.UniformReservoir;
import com.google.gerrit.metrics.MetricsReservoirConfig;
import com.google.gerrit.metrics.ReservoirType;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DropWizardReservoirProviderTest {
  private static final int SLIDING_WINDOW_INTERVAL = 1;
  private static final int SLIDING_WINDOW_SIZE = 256;

  @Mock private MetricsReservoirConfig configMock;

  @Test
  public void shouldInstantiateReservoirProviderBasedOnMetricsConfig() {
    when(configMock.reservoirType()).thenReturn(ReservoirType.ExponentiallyDecaying);
    assertThat(DropWizardReservoirProvider.get(configMock))
        .isInstanceOf(ExponentiallyDecayingReservoir.class);

    when(configMock.reservoirType()).thenReturn(ReservoirType.SlidingTimeWindow);
    when(configMock.reservoirWindow()).thenReturn(Duration.ofMinutes(1));
    assertThat(DropWizardReservoirProvider.get(configMock))
        .isInstanceOf(SlidingTimeWindowReservoir.class);

    when(configMock.reservoirType()).thenReturn(ReservoirType.SlidingTimeWindowArray);
    when(configMock.reservoirWindow()).thenReturn(Duration.ofMinutes(SLIDING_WINDOW_INTERVAL));
    assertThat(DropWizardReservoirProvider.get(configMock))
        .isInstanceOf(SlidingTimeWindowArrayReservoir.class);

    when(configMock.reservoirType()).thenReturn(ReservoirType.SlidingWindow);
    when(configMock.reservoirSize()).thenReturn(SLIDING_WINDOW_SIZE);
    assertThat(DropWizardReservoirProvider.get(configMock))
        .isInstanceOf(SlidingWindowReservoir.class);

    when(configMock.reservoirType()).thenReturn(ReservoirType.Uniform);
    assertThat(DropWizardReservoirProvider.get(configMock)).isInstanceOf(UniformReservoir.class);
  }
}
