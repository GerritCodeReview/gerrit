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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.extensions.client.SubmitType.FAST_FORWARD_ONLY;
import static com.google.gerrit.extensions.client.SubmitType.MERGE_IF_NECESSARY;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.bytes;
import static com.google.gerrit.server.cache.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.cache.proto.Cache.ConflictKeyProto;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ConflictKeyTest {
  @Test
  public void ffOnlyPreservesInputOrder() {
    ObjectId id1 = ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee");
    ObjectId id2 = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    ConflictKey id1First = ConflictKey.create(id1, id2, FAST_FORWARD_ONLY, true);
    ConflictKey id2First = ConflictKey.create(id2, id1, FAST_FORWARD_ONLY, true);

    assertThat(id1First)
        .isEqualTo(ConflictKey.createWithoutNormalization(id1, id2, FAST_FORWARD_ONLY, true));
    assertThat(id2First)
        .isEqualTo(ConflictKey.createWithoutNormalization(id2, id1, FAST_FORWARD_ONLY, true));
    assertThat(id1First).isNotEqualTo(id2First);
  }

  @Test
  public void nonFfOnlyNormalizesInputOrder() {
    ObjectId id1 = ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee");
    ObjectId id2 = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    ConflictKey id1First = ConflictKey.create(id1, id2, MERGE_IF_NECESSARY, true);
    ConflictKey id2First = ConflictKey.create(id2, id1, MERGE_IF_NECESSARY, true);
    ConflictKey expected =
        ConflictKey.createWithoutNormalization(id1, id2, MERGE_IF_NECESSARY, true);

    assertThat(id1First).isEqualTo(expected);
    assertThat(id2First).isEqualTo(expected);
  }

  @Test
  public void serializer() throws Exception {
    ConflictKey key =
        ConflictKey.create(
            ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee"),
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            SubmitType.MERGE_IF_NECESSARY,
            false);
    byte[] serialized = ConflictKey.Serializer.INSTANCE.serialize(key);
    assertThat(ConflictKeyProto.parseFrom(serialized))
        .isEqualTo(
            ConflictKeyProto.newBuilder()
                .setCommit(
                    bytes(
                        0xba, 0xdc, 0x0f, 0xee, 0xba, 0xdc, 0x0f, 0xee, 0xba, 0xdc, 0x0f, 0xee,
                        0xba, 0xdc, 0x0f, 0xee, 0xba, 0xdc, 0x0f, 0xee))
                .setOtherCommit(
                    bytes(
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef,
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef))
                .setSubmitType("MERGE_IF_NECESSARY")
                .setContentMerge(false)
                .build());
    assertThat(ConflictKey.Serializer.INSTANCE.deserialize(serialized)).isEqualTo(key);
  }

  /**
   * See {@link com.google.gerrit.server.cache.testing.SerializedClassSubject} for background and
   * what to do if this test fails.
   */
  @Test
  public void methods() throws Exception {
    assertThatSerializedClass(ConflictKey.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "commit", ObjectId.class,
                "otherCommit", ObjectId.class,
                "submitType", SubmitType.class,
                "contentMerge", boolean.class));
  }
}
