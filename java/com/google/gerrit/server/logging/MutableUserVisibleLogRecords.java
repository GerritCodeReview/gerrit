// Copyright (C) 2020 The Android Open Source Project
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
 * Thread-safe store for user visible log records.
 *
 * <p>This class is intended to keep track of user visible log records in {@link LoggingContext}. It
 * needs to be thread-safe because it gets shared between threads when the logging context is copied
 * to another thread (see {@link LoggingContextAwareRunnable} and {@link
 * LoggingContextAwareCallable}. In this case the logging contexts of both threads share the same
 * instance of this class. This is important since user visible log records are processed only at
 * the end of a request and user visible log records that are created in another thread should not
 * get lost.
 */
public class MutableUserVisibleLogRecords {
  private final ArrayList<String> userVisibleLogRecords = new ArrayList<>();

  public synchronized void add(String record) {
    userVisibleLogRecords.add(record);
  }

  public synchronized void set(List<String> records) {
    userVisibleLogRecords.clear();
    userVisibleLogRecords.addAll(records);
  }

  public synchronized ImmutableList<String> list() {
    return ImmutableList.copyOf(userVisibleLogRecords);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("userVisibleLogRecords", userVisibleLogRecords)
        .toString();
  }
}
