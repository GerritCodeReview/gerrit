// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.comment;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.server.comment.CommentContextCacheImpl.CommentContextSerializer;
import org.junit.Test;

public class CommentContextSerializerTest {
  @Test
  public void roundTrip() {
    CommentContext context =
        CommentContext.create(ImmutableMap.of(1, "line_1", 2, "line_2"), "java");
    byte[] serialized = CommentContextSerializer.INSTANCE.serialize(context);
    assertThat(CommentContextSerializer.INSTANCE.deserialize(serialized)).isEqualTo(context);
  }
}
