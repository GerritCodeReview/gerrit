/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.Test;

public class NotifyInfoProtoConverterTest {
  private final NotifyInfoProtoConverter notifyInfoProtoConverter =
      NotifyInfoProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    NotifyInfo notifyInfo = new NotifyInfo(ImmutableList.of("account1", "account2"));
    Entities.NotifyInfo proto = notifyInfoProtoConverter.toProto(notifyInfo);

    Entities.NotifyInfo expectedProto =
        Entities.NotifyInfo.newBuilder()
            .addAllAccounts(ImmutableList.of("account1", "account2"))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    NotifyInfo notifyInfo = new NotifyInfo(ImmutableList.of("account1", "account2"));

    NotifyInfo convertedNotifyInfo =
        notifyInfoProtoConverter.fromProto(notifyInfoProtoConverter.toProto(notifyInfo));

    assertThat(convertedNotifyInfo).isEqualTo(notifyInfo);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(NotifyInfo.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("accounts", new TypeLiteral<List<String>>() {}.getType())
                .build());
  }
}
