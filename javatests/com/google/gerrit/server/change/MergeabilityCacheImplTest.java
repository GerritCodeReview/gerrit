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

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.proto.Cache.MergeabilityKeyProto;
import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MergeabilityCacheImplTest {
  @Test
  public void keySerializer() throws Exception {
    MergeabilityCacheImpl.EntryKey key =
        new MergeabilityCacheImpl.EntryKey(
            ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee"),
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            SubmitType.MERGE_IF_NECESSARY,
            "aStrategy");
    CacheSerializer<MergeabilityCacheImpl.EntryKey> s =
        new MergeabilityCacheImpl.EntryKey.Serializer();
    byte[] serialized = s.serialize(key);
    assertThat(MergeabilityKeyProto.parseFrom(serialized))
        .isEqualTo(
            MergeabilityKeyProto.newBuilder()
                .setCommit(
                    bytes(
                        0xba, 0xdc, 0x0f, 0xee, 0xba, 0xdc, 0x0f, 0xee, 0xba, 0xdc, 0x0f, 0xee,
                        0xba, 0xdc, 0x0f, 0xee, 0xba, 0xdc, 0x0f, 0xee))
                .setInto(
                    bytes(
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef,
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef))
                .setSubmitType("MERGE_IF_NECESSARY")
                .setMergeStrategy("aStrategy")
                .build());
    assertThat(s.deserialize(serialized)).isEqualTo(key);
  }

  private static ByteString bytes(int... ints) {
    byte[] bytes = new byte[ints.length];
    for (int i = 0; i < ints.length; i++) {
      bytes[i] = (byte) ints[i];
    }
    return ByteString.copyFrom(bytes);
  }
}
