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

package com.google.gerrit.server.cache.serialize;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.proto.testing.Test.SerializableProto;
import org.junit.Test;

public class ProtobufSerializerTest {
  @Test
  public void requiredAndOptionalTypes() {
    assertRoundTrip(SerializableProto.newBuilder().setId(123));
    assertRoundTrip(SerializableProto.newBuilder().setId(123).setText("foo bar"));
  }

  @Test
  public void exactByteSequence() {
    ProtobufSerializer<SerializableProto> s = new ProtobufSerializer<>(SerializableProto.parser());
    SerializableProto proto = SerializableProto.newBuilder().setId(123).setText("foo bar").build();
    byte[] serialized = s.serialize(proto);
    // Hard-code byte sequence to detect library changes
    assertThat(serialized).isEqualTo(new byte[] {8, 123, 18, 7, 102, 111, 111, 32, 98, 97, 114});
  }

  private static void assertRoundTrip(SerializableProto.Builder input) {
    ProtobufSerializer<SerializableProto> s = new ProtobufSerializer<>(SerializableProto.parser());
    assertThat(s.deserialize(s.serialize(input.build()))).isEqualTo(input.build());
  }
}
