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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.bytes;
import static com.google.gerrit.server.cache.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.proto.Cache.ChangeKindKeyProto;
import com.google.gerrit.server.change.ChangeKindCacheImpl.Key;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ChangeKindCacheImplTest {
  @Test
  public void keySerializer() throws Exception {
    ChangeKindCacheImpl.Key key =
        Key.create(
            ObjectId.zeroId(),
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            "aStrategy");
    CacheSerializer<ChangeKindCacheImpl.Key> s = new ChangeKindCacheImpl.Key.Serializer();
    byte[] serialized = s.serialize(key);
    assertThat(ChangeKindKeyProto.parseFrom(serialized))
        .isEqualTo(
            ChangeKindKeyProto.newBuilder()
                .setPrior(bytes(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                .setNext(
                    bytes(
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef,
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef))
                .setStrategyName("aStrategy")
                .build());
    assertThat(s.deserialize(serialized)).isEqualTo(key);
  }

  /**
   * See {@link com.google.gerrit.server.cache.testing.SerializedClassSubject} for background and
   * what to do if this test fails.
   */
  @Test
  public void keyFields() throws Exception {
    assertThatSerializedClass(ChangeKindCacheImpl.Key.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "prior", ObjectId.class, "next", ObjectId.class, "strategyName", String.class));
  }
}
