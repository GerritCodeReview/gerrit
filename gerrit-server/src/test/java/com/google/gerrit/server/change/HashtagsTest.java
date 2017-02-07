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
  public void emptyCommitMessage() throws Exception {
    assertThat(HashtagsUtil.extractTags("")).isEmpty();
  }

  @Test
  public void nullCommitMessage() throws Exception {
    assertThat(HashtagsUtil.extractTags((String) null)).isEmpty();
  }

  @Test
  public void noHashtags() throws Exception {
    String commitMessage = "Subject\n\nLine 1\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage)).isEmpty();
  }

  @Test
  public void singleHashtag() throws Exception {
    String commitMessage = "#Subject\n\nLine 1\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("Subject"));
  }

  @Test
  public void singleHashtagNumeric() throws Exception {
    String commitMessage = "Subject\n\n#123\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("123"));
  }

  @Test
  public void multipleHashtags() throws Exception {
    String commitMessage = "#Subject\n\n#Hashtag\n\nLine 2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("Subject", "Hashtag"));
  }

  @Test
  public void repeatedHashtag() throws Exception {
    String commitMessage = "#Subject\n\n#Hashtag1\n\n#Hashtag2\n\n#Hashtag1";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("Subject", "Hashtag1", "Hashtag2"));
  }

  @Test
  public void multipleHashtagsNoSpaces() throws Exception {
    String commitMessage = "Subject\n\n#Hashtag1#Hashtag2";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("Hashtag1"));
  }

  @Test
  public void hyphenatedHashtag() throws Exception {
    String commitMessage = "Subject\n\n#Hyphenated-Hashtag";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("Hyphenated-Hashtag"));
  }

  @Test
  public void underscoredHashtag() throws Exception {
    String commitMessage = "Subject\n\n#Underscored_Hashtag";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("Underscored_Hashtag"));
  }

  @Test
  public void hashtagsWithAccentedCharacters() throws Exception {
    String commitMessage = "Jag #måste #öva på min #Svenska!\n\n" + "Jag behöver en #läkare.";
    assertThat(HashtagsUtil.extractTags(commitMessage))
        .containsExactlyElementsIn(Sets.newHashSet("måste", "öva", "Svenska", "läkare"));
  }

  @Test
  public void hashWithoutHashtag() throws Exception {
    String commitMessage = "Subject\n\n# Text";
    assertThat(HashtagsUtil.extractTags(commitMessage)).isEmpty();
  }
}
