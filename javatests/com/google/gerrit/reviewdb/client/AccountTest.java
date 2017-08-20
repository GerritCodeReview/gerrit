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

package com.google.gerrit.reviewdb.client;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.reviewdb.client.Account.Id.fromRef;
import static com.google.gerrit.reviewdb.client.Account.Id.fromRefPart;
import static com.google.gerrit.reviewdb.client.Account.Id.fromRefSuffix;

import org.junit.Test;

public class AccountTest {
  @Test
  public void parseRefName() {
    assertThat(fromRef("refs/users/01/1")).isEqualTo(id(1));
    assertThat(fromRef("refs/users/01/1-drafts")).isEqualTo(id(1));
    assertThat(fromRef("refs/users/01/1-drafts/2")).isEqualTo(id(1));
    assertThat(fromRef("refs/users/01/1/edit/2")).isEqualTo(id(1));

    assertThat(fromRef(null)).isNull();
    assertThat(fromRef("")).isNull();

    // Invalid characters.
    assertThat(fromRef("refs/users/01a/1")).isNull();
    assertThat(fromRef("refs/users/01/a1")).isNull();

    // Mismatched shard.
    assertThat(fromRef("refs/users/01/23")).isNull();

    // Shard too short.
    assertThat(fromRef("refs/users/1/1")).isNull();
  }

  @Test
  public void parseDraftCommentsRefName() {
    assertThat(fromRef("refs/draft-comments/35/135/1")).isEqualTo(id(1));
    assertThat(fromRef("refs/draft-comments/35/135/1-foo/2")).isEqualTo(id(1));
    assertThat(fromRef("refs/draft-comments/35/135/1/foo/2")).isEqualTo(id(1));

    // Invalid characters.
    assertThat(fromRef("refs/draft-comments/35a/135/1")).isNull();
    assertThat(fromRef("refs/draft-comments/35/135a/1")).isNull();
    assertThat(fromRef("refs/draft-comments/35/135/a1")).isNull();

    // Mismatched shard.
    assertThat(fromRef("refs/draft-comments/02/135/1")).isNull();

    // Shard too short.
    assertThat(fromRef("refs/draft-comments/2/2/1")).isNull();
  }

  @Test
  public void parseStarredChangesRefName() {
    assertThat(fromRef("refs/starred-changes/35/135/1")).isEqualTo(id(1));
    assertThat(fromRef("refs/starred-changes/35/135/1-foo/2")).isEqualTo(id(1));
    assertThat(fromRef("refs/starred-changes/35/135/1/foo/2")).isEqualTo(id(1));

    // Invalid characters.
    assertThat(fromRef("refs/starred-changes/35a/135/1")).isNull();
    assertThat(fromRef("refs/starred-changes/35/135a/1")).isNull();
    assertThat(fromRef("refs/starred-changes/35/135/a1")).isNull();

    // Mismatched shard.
    assertThat(fromRef("refs/starred-changes/02/135/1")).isNull();

    // Shard too short.
    assertThat(fromRef("refs/starred-changes/2/2/1")).isNull();
  }

  @Test
  public void parseRefNameParts() {
    assertThat(fromRefPart("01/1")).isEqualTo(id(1));
    assertThat(fromRefPart("ab/cd")).isNull();
  }

  @Test
  public void parseRefSuffix() {
    assertThat(fromRefSuffix("12/34")).isEqualTo(id(34));
    assertThat(fromRefSuffix("ab/cd")).isNull();
  }

  private Account.Id id(int n) {
    return new Account.Id(n);
  }
}
