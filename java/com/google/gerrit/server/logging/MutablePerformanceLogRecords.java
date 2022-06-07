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

package com.google.gerrit.server.logging;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe store for performance log records.
 *
 * <p>This class is intended to keep track of performance log records in {@link LoggingContext}. It
 * needs to be thread-safe because it gets shared between threads when the logging context is copied
 * to another thread (see {@link LoggingContextAwareRunnable} and {@link
 * LoggingContextAwareCallable}. In this case the logging contexts of both threads share the same
 * instance of this class. This is important since performance log records are processed only at the
 * end of a request and performance log records that are created in another thread should not get
 * lost.
 */
public class MutablePerformanceLogRecords {
  private final ArrayList<PerformanceLogRecord> performanceLogRecords = new ArrayList<>();

  public synchronized void add(PerformanceLogRecord record) {
    performanceLogRecords.add(record);
  }

  public synchronized void set(List<PerformanceLogRecord> records) {
    performanceLogRecords.clear();
    performanceLogRecords.addAll(records);
  }

  public synchronized ImmutableList<PerformanceLogRecord> list() {
    return ImmutableList.copyOf(performanceLogRecords);
  }

  public boolean isEmtpy() {
    return performanceLogRecords.isEmpty();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("performanceLogRecords", performanceLogRecords)
        .toString();
  }
}
