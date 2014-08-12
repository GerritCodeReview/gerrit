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

import com.google.common.base.Ticker;
import com.google.gerrit.common.TimeUtil;

import java.util.concurrent.TimeUnit;

public class TickerTimeProvider {
  private final long startMs;
  private final long startNs;
  private final Ticker ticker;

  TickerTimeProvider(long startMs, Ticker ticker) {
    this.ticker = ticker;
    this.startMs = startMs;
    this.startNs = ticker.read();
  }

  public TickerTimeProvider(Ticker ticker) {
    this(TimeUtil.nowMs(), ticker);
  }

  public long nowMs() {
    long ns = ticker.read() - startNs;
    return TimeUnit.NANOSECONDS.toMillis(ns) + startMs;
  }
}
