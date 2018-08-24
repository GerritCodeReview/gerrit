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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.util.RequestId;
import java.util.function.Consumer;

public class TraceContext implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final TraceContext DISABLED = new TraceContext();

  public static TraceContext open() {
    return new TraceContext();
  }

  public static <T> void invokeExtensionPoint(DynamicSet<T> dynamicSet, Consumer<T> c) {
    dynamicSet
        .entryIterator()
        .forEachRemaining(
            entry -> {
              T t = entry.getProvider().get();
              try (TraceContext traceContext = open().addTag("PLUGIN", entry.getPluginName())) {
                c.accept(t);
              } catch (RuntimeException e) {
                logger.atWarning().withCause(e).log(
                    "Failure in %s of plugin %s", t.getClass(), entry.getPluginName());
              }
            });
  }

  // Table<TAG_NAME, TAG_VALUE, REMOVE_ON_CLOSE>
  private final Table<String, String, Boolean> tags = HashBasedTable.create();

  private TraceContext() {}

  public TraceContext addTag(RequestId.Type requestId, Object tagValue) {
    return addTag(checkNotNull(requestId, "request ID is required").name(), tagValue);
  }

  public TraceContext addTag(String tagName, Object tagValue) {
    String name = checkNotNull(tagName, "tag name is required");
    String value = checkNotNull(tagValue, "tag value is required").toString();
    tags.put(name, value, LoggingContext.getInstance().addTag(name, value));
    return this;
  }

  @Override
  public void close() {
    for (Table.Cell<String, String, Boolean> cell : tags.cellSet()) {
      if (cell.getValue()) {
        LoggingContext.getInstance().removeTag(cell.getRowKey(), cell.getColumnKey());
      }
    }
  }
}
