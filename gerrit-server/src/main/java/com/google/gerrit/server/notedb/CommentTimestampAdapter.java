// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;

/**
 * Adapter that reads/writes {@link Timestamp}s as ISO 8601 instant in UTC.
 *
 * <p>This adapter reads and writes the ISO 8601 UTC instant format, {@code "2015-06-22T17:11:00Z"}.
 * This format is specially chosen because it is also readable by the default Gson type adapter,
 * despite the fact that the default adapter writes in a different format lacking timezones, {@code
 * "Jun 22, 2015 10:11:00 AM"}. Unlike the default adapter format, this representation is not
 * ambiguous during the transition away from DST.
 *
 * <p>This adapter is mutually compatible with the old adapter: the old adapter is able to read the
 * UTC instant format, and this adapter can fall back to parsing the old format.
 */
class CommentTimestampAdapter extends TypeAdapter<Timestamp> {
  private static final DateTimeFormatter FALLBACK =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

  @Override
  public void write(JsonWriter out, Timestamp ts) throws IOException {
    out.value(ISO_INSTANT.format(ts.toInstant()));
  }

  @Override
  public Timestamp read(JsonReader in) throws IOException {
    String str = in.nextString();
    TemporalAccessor ta;
    try {
      ta = ISO_INSTANT.parse(str);
    } catch (DateTimeParseException e) {
      ta = LocalDateTime.from(FALLBACK.parse(str)).atZone(ZoneId.systemDefault());
    }
    return Timestamp.from(Instant.from(ta));
  }
}
