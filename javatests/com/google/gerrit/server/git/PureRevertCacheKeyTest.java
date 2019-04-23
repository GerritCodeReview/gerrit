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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteArray;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class PureRevertCacheKeyTest {
  @Test
  public void serialization() {
    ObjectId revert = ObjectId.zeroId();
    ObjectId original = ObjectId.fromString("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    byte[] serializedRevert =
        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    byte[] serializedOriginal =
        byteArray(
            0xaa, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
            0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb);

    Cache.PureRevertKeyProto key = PureRevertCache.key(Project.nameKey("test"), revert, original);
    assertThat(key)
        .isEqualTo(
            Cache.PureRevertKeyProto.newBuilder()
                .setProject("test")
                .setClaimedRevert(ByteString.copyFrom(serializedRevert))
                .setClaimedOriginal(ByteString.copyFrom(serializedOriginal))
                .build());
  }
}
