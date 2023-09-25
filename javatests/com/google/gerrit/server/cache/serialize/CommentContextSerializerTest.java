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

package com.google.gerrit.server.cache.serialize;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.comment.CommentContextCacheImpl.CommentContextSerializer;
import com.google.gerrit.server.comment.CommentContextKey;
import org.junit.Test;

public class CommentContextSerializerTest {
  @Test
  public void roundTripValue() {
    CommentContext commentContext =
        CommentContext.create(ImmutableMap.of(1, "line_1", 2, "line_2"), "text/x-java");

    byte[] serialized = CommentContextSerializer.INSTANCE.serialize(commentContext);
    CommentContext deserialized = CommentContextSerializer.INSTANCE.deserialize(serialized);

    assertThat(commentContext).isEqualTo(deserialized);
  }

  @Test
  public void roundTripKey() {
    Project.NameKey proj = Project.NameKey.parse("project");
    Change.Id changeId = Change.Id.tryParse("1234", "project").get();

    CommentContextKey k =
        CommentContextKey.builder()
            .project(proj)
            .changeId(changeId)
            .id("commentId")
            .path("pathHash")
            .patchset(1)
            .contextPadding(3)
            .build();
    byte[] serialized = CommentContextKey.Serializer.INSTANCE.serialize(k);
    assertThat(k).isEqualTo(CommentContextKey.Serializer.INSTANCE.deserialize(serialized));
  }
}
