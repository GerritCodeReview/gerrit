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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.cache.serialize.entities.StoredCommentLinkInfoSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.StoredCommentLinkInfoSerializer.serialize;

import com.google.gerrit.entities.StoredCommentLinkInfo;
import org.junit.Test;

public class StoredCommentLinkInfoSerializerTest {
  static final StoredCommentLinkInfo HTML_ONLY =
      StoredCommentLinkInfo.builder("name")
          .setEnabled(true)
          .setHtml("<p>html")
          .setMatch("*")
          .build();

  @Test
  public void htmlOnly_roundTrip() {
    assertThat(deserialize(serialize(HTML_ONLY))).isEqualTo(HTML_ONLY);
  }

  @Test
  public void linkOnly_roundTrip() {
    StoredCommentLinkInfo autoValue =
        StoredCommentLinkInfo.builder("name")
            .setEnabled(true)
            .setLink("<p>html")
            .setMatch("*")
            .build();
    assertThat(deserialize(serialize(autoValue))).isEqualTo(autoValue);
  }

  @Test
  public void overrideOnly_roundTrip() {
    StoredCommentLinkInfo autoValue =
        StoredCommentLinkInfo.builder("name")
            .setEnabled(true)
            .setOverrideOnly(true)
            .setLink("<p>html")
            .setMatch("*")
            .build();
    assertThat(deserialize(serialize(autoValue))).isEqualTo(autoValue);
  }
}
