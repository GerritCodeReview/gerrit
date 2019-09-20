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
import com.google.gerrit.acceptance.UseSystemTime;
import com.google.gerrit.server.util.time.TimeUtil;
import org.junit.Test;

@UseClockStep
public class UseSystemTimeTest extends AbstractDaemonTest {
  @Test
  @UseSystemTime
  public void useSystemTimeAlthoughClassIsAnnotatedWithUseClockStep() {
    long firstTimestamp = TimeUtil.nowMs();
    long secondTimestamp = TimeUtil.nowMs();
    assertThat(secondTimestamp - firstTimestamp).isLessThan(1000);
  }
}
