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

package com.google.gerrit.entities;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ChangeTest {
  @Test
  public void parseInvalidRefNames() {
    assertNotRef(null);
    assertNotRef("");
    assertNotRef("01/1/1");
    assertNotRef("HEAD");
    assertNotRef("refs/tags/v1");
  }

  @Test
  public void parsePatchSetRefNames() {
    assertRef(1, "refs/changes/01/1/1");
    assertRef(1234, "refs/changes/34/1234/56");

    // Invalid characters.
    assertNotRef("refs/changes/0x/1/1");
    assertNotRef("refs/changes/01/x/1");
    assertNotRef("refs/changes/01/1/x");

    // Truncations.
    assertNotRef("refs/changes/");
    assertNotRef("refs/changes/1");
    assertNotRef("refs/changes/01");
    assertNotRef("refs/changes/01/");
    assertNotRef("refs/changes/01/1/");
    assertNotRef("refs/changes/01/1/1/");
    assertNotRef("refs/changes/01//1/1");

    // Leading zeroes.
    assertNotRef("refs/changes/01/01/1");
    assertNotRef("refs/changes/01/1/01");

    // Mismatched last 2 digits.
    assertNotRef("refs/changes/35/1234/56");

    // Something other than patch set after change.
    assertNotRef("refs/changes/34/1234/0");
    assertNotRef("refs/changes/34/1234/foo");
    assertNotRef("refs/changes/34/1234|56");
    assertNotRef("refs/changes/34/1234foo");
  }

  @Test
  public void parseEditRefNames() {
    assertRef(5, "refs/users/34/1234/edit-5/1");
    assertRef(5, "refs/users/34/1234/edit-5");
    assertNotRef("refs/changes/34/1234/edit-5/1");
    assertNotRef("refs/users/34/1234/EDIT-5/1");
    assertNotRef("refs/users/34/1234");
  }

  @Test
  public void parseChangeMetaRefNames() {
    assertRef(1, "refs/changes/01/1/meta");
    assertRef(1234, "refs/changes/34/1234/meta");

    assertNotRef("refs/changes/01/1/met");
    assertNotRef("refs/changes/01/1/META");
    assertNotRef("refs/changes/01/1/1/meta");
  }

  @Test
  public void parseRobotCommentRefNames() {
    assertRef(1, "refs/changes/01/1/robot-comments");
    assertRef(1234, "refs/changes/34/1234/robot-comments");

    assertNotRef("refs/changes/01/1/robot-comment");
    assertNotRef("refs/changes/01/1/ROBOT-COMMENTS");
    assertNotRef("refs/changes/01/1/1/robot-comments");
  }

  @Test
  public void parseStarredChangesRefNames() {
    assertAllUsersRef(1, "refs/starred-changes/01/1/1001");
    assertAllUsersRef(1234, "refs/starred-changes/34/1234/1001");

    assertNotRef("refs/starred-changes/01/1/1001");
    assertNotAllUsersRef(null);
    assertNotAllUsersRef("refs/starred-changes/01/1/1xx1");
    assertNotAllUsersRef("refs/starred-changes/01/1/");
    assertNotAllUsersRef("refs/starred-changes/01/1");
    assertNotAllUsersRef("refs/starred-changes/35/1234/1001");
    assertNotAllUsersRef("refs/starred-changeS/01/1/1001");
  }

  @Test
  public void parseDraftRefNames() {
    assertAllUsersRef(1, "refs/draft-comments/01/1/1001");
    assertAllUsersRef(1234, "refs/draft-comments/34/1234/1001");

    assertNotRef("refs/draft-comments/01/1/1001");
    assertNotAllUsersRef(null);
    assertNotAllUsersRef("refs/draft-comments/01/1/1xx1");
    assertNotAllUsersRef("refs/draft-comments/01/1/");
    assertNotAllUsersRef("refs/draft-comments/01/1");
    assertNotAllUsersRef("refs/draft-comments/35/1234/1001");
    assertNotAllUsersRef("refs/draft-commentS/01/1/1001");
  }

  @Test
  public void toRefPrefix() {
    assertThat(Change.id(1).toRefPrefix()).isEqualTo("refs/changes/01/1/");
    assertThat(Change.id(1234).toRefPrefix()).isEqualTo("refs/changes/34/1234/");
  }

  @Test
  public void parseRefNameParts() {
    assertRefPart(1, "01/1");

    assertNotRefPart(null);
    assertNotRefPart("");

    // This method assumes that the common prefix "refs/changes/" was removed.
    assertNotRefPart("refs/changes/01/1");

    // Invalid characters.
    assertNotRefPart("01a/1");
    assertNotRefPart("01/a1");

    // Mismatched shard.
    assertNotRefPart("01/23");

    // Shard too short.
    assertNotRefPart("1/1");
  }

  @Test
  public void idToString() {
    assertThat(Change.id(3).toString()).isEqualTo("3");
  }

  @Test
  public void keyToString() {
    String key = "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    assertThat(ObjectId.isId(key.substring(1))).isTrue();
    assertThat(Change.key(key).toString()).isEqualTo(key);
  }

  private static void assertRef(int changeId, String refName) {
    assertThat(Change.Id.fromRef(refName)).isEqualTo(Change.id(changeId));
  }

  private static void assertNotRef(String refName) {
    assertThat(Change.Id.fromRef(refName)).isNull();
  }

  private static void assertAllUsersRef(int changeId, String refName) {
    assertThat(Change.Id.fromAllUsersRef(refName)).isEqualTo(Change.id(changeId));
  }

  private static void assertNotAllUsersRef(String refName) {
    assertThat(Change.Id.fromAllUsersRef(refName)).isNull();
  }

  private static void assertRefPart(int changeId, String refName) {
    assertEquals(Change.id(changeId), Change.Id.fromRefPart(refName));
  }

  private static void assertNotRefPart(String refName) {
    assertNull(Change.Id.fromRefPart(refName));
  }
}
