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
import static com.google.gerrit.server.cache.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.proto.reviewdb.Reviewdb;
import com.google.gerrit.reviewdb.client.Account;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import org.junit.Test;

public class AccountIdProtoConverterTest {
  private final AccountIdProtoConverter accountIdProtoConverter = AccountIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    Account.Id accountId = new Account.Id(24);

    Reviewdb.Account_Id proto = accountIdProtoConverter.toProto(accountId);

    Reviewdb.Account_Id expectedProto = Reviewdb.Account_Id.newBuilder().setId(24).build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    Account.Id accountId = new Account.Id(34832);

    Account.Id convertedAccountId =
        accountIdProtoConverter.fromProto(accountIdProtoConverter.toProto(accountId));

    assertThat(convertedAccountId).isEqualTo(accountId);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Reviewdb.Account_Id proto = Reviewdb.Account_Id.newBuilder().setId(24).build();
    byte[] bytes = proto.toByteArray();

    Parser<Reviewdb.Account_Id> parser = accountIdProtoConverter.getParser();
    Reviewdb.Account_Id parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /**
   * See {@link com.google.gerrit.server.cache.testing.SerializedClassSubject} for background and
   * what to do if this test fails.
   */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(Account.Id.class)
        .hasFields(ImmutableMap.<String, Type>builder().put("id", int.class).build());
  }
}
