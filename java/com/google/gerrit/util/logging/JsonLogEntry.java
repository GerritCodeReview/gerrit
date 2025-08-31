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

package com.google.gerrit.util.logging;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;

public abstract class JsonLogEntry {
  /**
   * Retrieves the value associated with the given MDC key from a LogEvent.
   *
   * @param event the Log4j2 log event
   * @param key the MDC key
   * @return the MDC value or null if not set
   */
  public String getMdcString(LogEvent event, String key) {
    return event.getContextData().getValue(key);
  }

  /** Alternative: retrieve directly from ThreadContext of the current thread. */
  public String getMdcString(String key) {
    return ThreadContext.get(key);
  }
}
