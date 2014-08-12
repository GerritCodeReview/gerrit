// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.cache.h2;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.server.cache.AdjustableTicker;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class TickerTimeProviderTest {

  @Test
  public void testInitial() throws Exception {
    AdjustableTicker ticker = new AdjustableTicker();
    long start = TimeUtil.nowMs();
    TickerTimeProvider classUnderTest = new TickerTimeProvider(start, ticker);

    assertThat(start).isEqualTo(classUnderTest.nowMs());
  }

  @Test
  public void testAdjusted() throws Exception {
    AdjustableTicker ticker = new AdjustableTicker();
    long start = TimeUtil.nowMs();
    TickerTimeProvider classUnderTest = new TickerTimeProvider(start, ticker);
    int deltaMs = 785;
    ticker.set(TimeUnit.MILLISECONDS.toNanos(deltaMs));

    assertThat(start + 785).isEqualTo(classUnderTest.nowMs());
  }
}
