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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Formatter for timestamps used in log entries. */
public class LogTimestampFormatter {
  public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  private final DateTimeFormatter dateFormatter;
  private final ZoneOffset timeOffset;

  public LogTimestampFormatter() {
    dateFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT);
    timeOffset = OffsetDateTime.now(ZoneId.systemDefault()).getOffset();
  }

  /**
   * Formats time given in milliseconds since UNIX epoch to ISO 8601 format.
   *
   * @param epochMilli milliseconds since UNIX epoch
   * @return ISO 8601-formatted timestamp
   */
  public String format(long epochMilli) {
    return ZonedDateTime.of(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), timeOffset),
            ZoneId.systemDefault())
        .format(dateFormatter);
  }
}
