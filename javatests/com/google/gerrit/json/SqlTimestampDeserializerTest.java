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

package com.google.gerrit.json;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.gson.JsonPrimitive;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class SqlTimestampDeserializerTest {

  private final SqlTimestampDeserializer deserializer = new SqlTimestampDeserializer();

  @Test
  public void emptyStringIsDeserializedToMagicTimestamp() {
    Timestamp timestamp = deserializer.deserialize(new JsonPrimitive(""), Timestamp.class, null);
    assertThat(timestamp).isEqualTo(Timestamp.from(Instant.EPOCH));
  }

  @Test
  public void serializesTheSameInEveryDefaultLocale() {
    List<Timestamp> timestamps =
        List.of(
            Timestamp.from(Instant.parse("2026-11-26T14:34:35.160Z")),
            Timestamp.from(Instant.parse("1000-01-01T00:00:00Z")),
            Timestamp.from(Instant.parse("+10000-01-01T00:00:00Z")));
    Locale saved = Locale.getDefault();
    try {
      Locale.setDefault(Locale.US);
      List<String> expected =
          timestamps.stream()
              .map(t -> deserializer.serialize(t, Timestamp.class, null).getAsString())
              .toList();
      for (Locale l : List.of(Locale.forLanguageTag("th-TH"), Locale.forLanguageTag("ar-EG"))) {
        Locale.setDefault(l);
        assertWithMessage("%s", l)
            .that(
                timestamps.stream()
                    .map(t -> deserializer.serialize(t, Timestamp.class, null).getAsString())
                    .toList())
            .isEqualTo(expected);
      }
    } finally {
      Locale.setDefault(saved);
    }
  }
}
