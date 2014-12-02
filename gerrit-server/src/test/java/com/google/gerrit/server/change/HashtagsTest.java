// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.Sets;

import org.junit.Test;

public class HashtagsTest {
  @Test
  public void emptyCommitMessage() {
    assertThat(HashtagsUtil.extractTags("")).isEmpty();
  }

  @Test
  public void nullCommitMessage() {
    assertThat(HashtagsUtil.extractTags(null)).isEmpty();
  }

  @Test
  public void noHashtags() {
    String commitMessage = "Subject\n\nLine 1\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage)).isEmpty();
  }

  @Test
  public void singleHashtag() {
    String commitMessage = "#Subject\n\nLine 1\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
      .containsExactlyElementsIn(Sets.newHashSet("#Subject"));
  }

  @Test
  public void multipleHashtags() {
    String commitMessage = "#Subject\n\n#Hashtag\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
      .containsExactlyElementsIn(Sets.newHashSet("#Subject", "#Hashtag"));
  }

  @Test
  public void multipleSameHashtag() {
    String commitMessage = "#Subject\n\n#Hashtag1\n\n#Hashtag2\n\n#Hashtag1";
    assertThat(HashtagsUtil.extractTags(commitMessage))
      .containsExactlyElementsIn(
          Sets.newHashSet("#Subject", "#Hashtag1", "#Hashtag2"));
  }

  @Test
  public void multipleHashtagsNoSpaces() {
    String commitMessage = "Subject\n\n#Hashtag1#Hashtag2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
      .containsExactlyElementsIn(Sets.newHashSet("#Hashtag1", "#Hashtag2"));
  }
}
