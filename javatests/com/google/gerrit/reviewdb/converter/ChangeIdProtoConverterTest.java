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

package com.google.gerrit.reviewdb.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.proto.reviewdb.Reviewdb;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.reviewdb.client.Change;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import org.junit.Test;

public class ChangeIdProtoConverterTest {
  private final ChangeIdProtoConverter changeIdProtoConverter = ChangeIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    Change.Id changeId = new Change.Id(94);

    Reviewdb.Change_Id proto = changeIdProtoConverter.toProto(changeId);

    Reviewdb.Change_Id expectedProto = Reviewdb.Change_Id.newBuilder().setId(94).build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    Change.Id changeId = new Change.Id(2903482);

    Change.Id convertedChangeId =
        changeIdProtoConverter.fromProto(changeIdProtoConverter.toProto(changeId));

    assertThat(convertedChangeId).isEqualTo(changeId);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Reviewdb.Change_Id proto = Reviewdb.Change_Id.newBuilder().setId(94).build();
    byte[] bytes = proto.toByteArray();

    Parser<Reviewdb.Change_Id> parser = changeIdProtoConverter.getParser();
    Reviewdb.Change_Id parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(Change.Id.class)
        .hasFields(ImmutableMap.<String, Type>builder().put("id", int.class).build());
  }
}
