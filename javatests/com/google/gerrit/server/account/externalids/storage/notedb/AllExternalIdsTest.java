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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteString;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.storage.notedb.AllExternalIds.Serializer;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalIdsProto.ExternalIdProto;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Type;
import java.util.Arrays;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AllExternalIdsTest {
  private ExternalIdFactory externalIdFactory;

  @Mock AuthConfig authConfig;

  @Before
  public void setUp() throws Exception {
    externalIdFactory =
        new ExternalIdFactory(
            new ExternalIdKeyFactory(
                new ExternalIdKeyFactory.Config() {
                  @Override
                  public boolean isUserNameCaseInsensitive() {
                    return false;
                  }
                }),
            authConfig);
  }

  @Test
  public void serializeEmptyExternalIds() throws Exception {
    assertRoundTrip(allExternalIds(), AllExternalIdsProto.getDefaultInstance());
  }

  @Test
  public void serializeMultipleExternalIds() throws Exception {
    Account.Id accountId1 = Account.id(1001);
    Account.Id accountId2 = Account.id(1002);
    assertRoundTrip(
        allExternalIds(
            externalIdFactory.create("scheme1", "id1", accountId1),
            externalIdFactory.create("scheme2", "id2", accountId1),
            externalIdFactory.create("scheme2", "id3", accountId2),
            externalIdFactory.create("scheme3", "id4", accountId2)),
        AllExternalIdsProto.newBuilder()
            .addExternalId(
                ExternalIdProto.newBuilder().setKey("scheme1:id1").setAccountId(1001).build())
            .addExternalId(
                ExternalIdProto.newBuilder().setKey("scheme2:id2").setAccountId(1001).build())
            .addExternalId(
                ExternalIdProto.newBuilder().setKey("scheme2:id3").setAccountId(1002).build())
            .addExternalId(
                ExternalIdProto.newBuilder().setKey("scheme3:id4").setAccountId(1002).build())
            .build());
  }

  @Test
  public void serializeExternalIdWithEmail() throws Exception {
    assertRoundTrip(
        allExternalIds(externalIdFactory.createEmail(Account.id(1001), "foo@example.com")),
        AllExternalIdsProto.newBuilder()
            .addExternalId(
                ExternalIdProto.newBuilder()
                    .setKey("mailto:foo@example.com")
                    .setAccountId(1001)
                    .setEmail("foo@example.com"))
            .build());
  }

  @Test
  public void serializeExternalIdWithPassword() throws Exception {
    assertRoundTrip(
        allExternalIds(
            externalIdFactory.create("scheme", "id", Account.id(1001), null, "hashed password")),
        AllExternalIdsProto.newBuilder()
            .addExternalId(
                ExternalIdProto.newBuilder()
                    .setKey("scheme:id")
                    .setAccountId(1001)
                    .setPassword("hashed password"))
            .build());
  }

  @Test
  public void serializeExternalIdWithBlobId() throws Exception {
    assertRoundTrip(
        allExternalIds(
            externalIdFactory.create(
                externalIdFactory.create("scheme", "id", Account.id(1001)),
                ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))),
        AllExternalIdsProto.newBuilder()
            .addExternalId(
                ExternalIdProto.newBuilder()
                    .setKey("scheme:id")
                    .setAccountId(1001)
                    .setBlobId(
                        byteString(
                            0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef,
                            0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef)))
            .build());
  }

  @Test
  public void allExternalIdsMethods() {
    assertThatSerializedClass(AllExternalIds.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "byKey",
                new TypeLiteral<ImmutableMap<ExternalId.Key, ExternalId>>() {}.getType(),
                "byAccount",
                new TypeLiteral<ImmutableSetMultimap<Account.Id, ExternalId>>() {}.getType(),
                "byEmail",
                new TypeLiteral<ImmutableSetMultimap<String, ExternalId>>() {}.getType()));
  }

  @Test
  public void externalIdMethods() {
    assertThatSerializedClass(ExternalId.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("key", ExternalId.Key.class)
                .put("accountId", Account.Id.class)
                .put("isCaseInsensitive", boolean.class)
                .put("email", String.class)
                .put("password", String.class)
                .put("blobId", ObjectId.class)
                .build());
  }

  private static AllExternalIds allExternalIds(ExternalId... externalIds) {
    return AllExternalIds.create(Arrays.stream(externalIds));
  }

  private static void assertRoundTrip(
      AllExternalIds allExternalIds, AllExternalIdsProto expectedProto) throws Exception {
    AllExternalIdsProto actualProto =
        AllExternalIdsProto.parseFrom(Serializer.INSTANCE.serialize(allExternalIds));
    assertThat(actualProto).ignoringRepeatedFieldOrder().isEqualTo(expectedProto);
    AllExternalIds actual =
        Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(allExternalIds));
    assertThat(actual).isEqualTo(allExternalIds);
  }
}
