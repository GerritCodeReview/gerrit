// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.annotation;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class UseClockStepTest extends AbstractDaemonTest {
  @Test
  @UseClockStep
  public void useClockStepWithDefaults() {
    long firstTimestamp = TimeUtil.nowMs();
    long secondTimestamp = TimeUtil.nowMs();
    assertThat(secondTimestamp - firstTimestamp).isEqualTo(1000);
  }

  @Test
  @UseClockStep(clockStepUnit = TimeUnit.MINUTES)
  public void useClockStepWithTimeUnit() {
    long firstTimestamp = TimeUtil.nowMs();
    long secondTimestamp = TimeUtil.nowMs();
    assertThat(secondTimestamp - firstTimestamp).isEqualTo(60 * 1000);
  }

  @Test
  @UseClockStep(clockStep = 5)
  public void useClockStepWithClockStep() {
    long firstTimestamp = TimeUtil.nowMs();
    long secondTimestamp = TimeUtil.nowMs();
    assertThat(secondTimestamp - firstTimestamp).isEqualTo(5 * 1000);
  }

  @Test
  @UseClockStep(startAtEpoch = true)
  public void useClockStepWithStartAtEpoch() {
    assertThat(TimeUtil.nowTs()).isEqualTo(Timestamp.from(Instant.EPOCH));
  }
}
