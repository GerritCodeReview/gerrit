// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.proto.Cache.ProjectWatchKeyProto;
import org.junit.Test;

/**
 * Test to ensure that we are serializing and deserializing {@link ProjectWatches.ProjectWatchKey}
 * correctly. This is part of the {@code AccountCache}.
 */
public class ProjectWatchCacheTest {
  @Test
  public void keyRoundTrip() throws Exception {
    ProjectWatches.ProjectWatchKey key =
        ProjectWatches.ProjectWatchKey.create(Project.nameKey("pro/ject"), "*");
    byte[] serialized = ProjectWatches.ProjectWatchKey.Serializer.INSTANCE.serialize(key);
    ProtoTruth.assertThat(ProjectWatchKeyProto.parseFrom(serialized))
        .isEqualTo(ProjectWatchKeyProto.newBuilder().setProject("pro/ject").setFilter("*").build());
    assertThat(ProjectWatches.ProjectWatchKey.Serializer.INSTANCE.deserialize(serialized))
        .isEqualTo(key);
  }

  @Test
  public void keyRoundTripNullFilter() throws Exception {
    ProjectWatches.ProjectWatchKey key =
        ProjectWatches.ProjectWatchKey.create(Project.nameKey("pro/ject"), null);
    byte[] serialized = ProjectWatches.ProjectWatchKey.Serializer.INSTANCE.serialize(key);
    ProtoTruth.assertThat(ProjectWatchKeyProto.parseFrom(serialized))
        .isEqualTo(ProjectWatchKeyProto.newBuilder().setProject("pro/ject").build());
    assertThat(ProjectWatches.ProjectWatchKey.Serializer.INSTANCE.deserialize(serialized))
        .isEqualTo(key);
  }
}
