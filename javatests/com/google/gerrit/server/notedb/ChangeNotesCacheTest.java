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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteString;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesKeyProto;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public final class ChangeNotesCacheTest {
  @Test
  public void keySerializer() throws Exception {
    ChangeNotesCache.Key key =
        ChangeNotesCache.Key.create(
            Project.nameKey("project"),
            Change.id(1234),
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    byte[] serialized = ChangeNotesCache.Key.Serializer.INSTANCE.serialize(key);
    assertThat(ChangeNotesKeyProto.parseFrom(serialized))
        .isEqualTo(
            ChangeNotesKeyProto.newBuilder()
                .setProject("project")
                .setChangeId(1234)
                .setId(
                    byteString(
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef,
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef))
                .build());
    assertThat(ChangeNotesCache.Key.Serializer.INSTANCE.deserialize(serialized)).isEqualTo(key);
  }

  @Test
  public void keyMethods() throws Exception {
    assertThatSerializedClass(ChangeNotesCache.Key.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "project", Project.NameKey.class,
                "changeId", Change.Id.class,
                "id", ObjectId.class));
  }
}
