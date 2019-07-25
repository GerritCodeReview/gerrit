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

import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.JsonPrimitive;
import java.sql.Timestamp;
import org.junit.Test;

public class SqlTimestampDeserializerTest {

  private final SqlTimestampDeserializer deserializer = new SqlTimestampDeserializer();

  @Test
  public void emptyStringIsDeserializedToMagicTimestamp() {
    Timestamp timestamp = deserializer.deserialize(new JsonPrimitive(""), Timestamp.class, null);
    assertThat(timestamp).isEqualTo(TimeUtil.never());
  }
}
