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

package com.google.gerrit.proto;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.gerrit.server.cache.proto.Cache.ChangeNotesKeyProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import org.junit.Test;

public class ProtosTest {
  @Test
  public void parseUncheckedByteArrayWrongProtoType() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    byte[] bytes = Protos.toByteArray(proto);
    assertThrows(
        IllegalArgumentException.class,
        () -> Protos.parseUnchecked(ChangeNotesStateProto.parser(), bytes));
  }

  @Test
  public void parseUncheckedByteArrayInvalidData() {
    byte[] bytes = new byte[] {0x00};
    assertThrows(
        IllegalArgumentException.class,
        () -> Protos.parseUnchecked(ChangeNotesStateProto.parser(), bytes));
  }

  @Test
  public void parseUncheckedByteArray() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    byte[] bytes = Protos.toByteArray(proto);
    assertThat(Protos.parseUnchecked(ChangeNotesKeyProto.parser(), bytes)).isEqualTo(proto);
  }

  @Test
  public void parseUncheckedSegmentOfByteArrayWrongProtoType() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    byte[] bytes = Protos.toByteArray(proto);
    assertThrows(
        IllegalArgumentException.class,
        () -> Protos.parseUnchecked(ChangeNotesStateProto.parser(), bytes, 0, bytes.length));
  }

  @Test
  public void parseUncheckedSegmentOfByteArrayInvalidData() {
    byte[] bytes = new byte[] {0x00};
    assertThrows(
        IllegalArgumentException.class,
        () -> Protos.parseUnchecked(ChangeNotesStateProto.parser(), bytes, 0, bytes.length));
  }

  @Test
  public void parseUncheckedSegmentOfByteArray() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    byte[] protoBytes = Protos.toByteArray(proto);
    int offset = 3;
    int length = protoBytes.length;
    byte[] bytes = new byte[length + 20];
    Arrays.fill(bytes, (byte) 1);
    System.arraycopy(protoBytes, 0, bytes, offset, length);

    ChangeNotesKeyProto parsedProto =
        Protos.parseUnchecked(ChangeNotesKeyProto.parser(), bytes, offset, length);

    assertThat(parsedProto).isEqualTo(proto);
  }

  @Test
  public void parseUncheckedByteStringWrongProtoType() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    ByteString byteString = Protos.toByteString(proto);
    assertThrows(
        IllegalArgumentException.class,
        () -> Protos.parseUnchecked(ChangeNotesStateProto.parser(), byteString));
  }

  @Test
  public void parseUncheckedByteStringInvalidData() {
    ByteString byteString = ByteString.copyFrom(new byte[] {0x00});
    assertThrows(
        IllegalArgumentException.class,
        () -> Protos.parseUnchecked(ChangeNotesStateProto.parser(), byteString));
  }

  @Test
  public void parseUncheckedByteString() {
    ChangeNotesKeyProto proto =
        ChangeNotesKeyProto.newBuilder()
            .setProject("project")
            .setChangeId(1234)
            .setId(ByteString.copyFromUtf8("foo"))
            .build();
    ByteString byteString = Protos.toByteString(proto);
    assertThat(Protos.parseUnchecked(ChangeNotesKeyProto.parser(), byteString)).isEqualTo(proto);
  }
}
