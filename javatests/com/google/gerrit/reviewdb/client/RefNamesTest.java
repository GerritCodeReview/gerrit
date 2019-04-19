// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.reviewdb.client.RefNames.parseAfterShardedRefPart;
import static com.google.gerrit.reviewdb.client.RefNames.parseRefSuffix;
import static com.google.gerrit.reviewdb.client.RefNames.parseShardedRefPart;
import static com.google.gerrit.reviewdb.client.RefNames.parseShardedUuidFromRefPart;
import static com.google.gerrit.reviewdb.client.RefNames.skipShardedRefPart;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RefNamesTest {
  private static final String TEST_GROUP_UUID = "ccab3195282a8ce4f5014efa391e82d10f884c64";
  private static final String TEST_SHARDED_GROUP_UUID =
      TEST_GROUP_UUID.substring(0, 2) + "/" + TEST_GROUP_UUID;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private final Account.Id accountId = new Account.Id(1011123);
  private final Change.Id changeId = new Change.Id(67473);
  private final PatchSet.Id psId = new PatchSet.Id(changeId, 42);

  @Test
  public void fullName() throws Exception {
    assertThat(RefNames.fullName(RefNames.REFS_CONFIG)).isEqualTo(RefNames.REFS_CONFIG);
    assertThat(RefNames.fullName("refs/heads/master")).isEqualTo("refs/heads/master");
    assertThat(RefNames.fullName("master")).isEqualTo("refs/heads/master");
    assertThat(RefNames.fullName("refs/tags/v1.0")).isEqualTo("refs/tags/v1.0");
    assertThat(RefNames.fullName("HEAD")).isEqualTo("HEAD");
  }

  @Test
  public void changeRefs() throws Exception {
    String changeMetaRef = RefNames.changeMetaRef(changeId);
    assertThat(changeMetaRef).isEqualTo("refs/changes/73/67473/meta");
    assertThat(RefNames.isNoteDbMetaRef(changeMetaRef)).isTrue();

    String robotCommentsRef = RefNames.robotCommentsRef(changeId);
    assertThat(robotCommentsRef).isEqualTo("refs/changes/73/67473/robot-comments");
    assertThat(RefNames.isNoteDbMetaRef(robotCommentsRef)).isTrue();
  }

  @Test
  public void refForGroupIsSharded() throws Exception {
    AccountGroup.UUID groupUuid = new AccountGroup.UUID("ABCDEFG");
    String groupRef = RefNames.refsGroups(groupUuid);
    assertThat(groupRef).isEqualTo("refs/groups/AB/ABCDEFG");
  }

  @Test
  public void refForGroupWithUuidLessThanTwoCharsIsRejected() throws Exception {
    AccountGroup.UUID groupUuid = new AccountGroup.UUID("A");
    expectedException.expect(IllegalArgumentException.class);
    RefNames.refsGroups(groupUuid);
  }

  @Test
  public void refForDeletedGroupIsSharded() throws Exception {
    AccountGroup.UUID groupUuid = new AccountGroup.UUID("ABCDEFG");
    String groupRef = RefNames.refsDeletedGroups(groupUuid);
    assertThat(groupRef).isEqualTo("refs/deleted-groups/AB/ABCDEFG");
  }

  @Test
  public void refForDeletedGroupWithUuidLessThanTwoCharsIsRejected() throws Exception {
    AccountGroup.UUID groupUuid = new AccountGroup.UUID("A");
    expectedException.expect(IllegalArgumentException.class);
    RefNames.refsDeletedGroups(groupUuid);
  }

  @Test
  public void refsUsers() throws Exception {
    assertThat(RefNames.refsUsers(accountId)).isEqualTo("refs/users/23/1011123");
  }

  @Test
  public void refsDraftComments() throws Exception {
    assertThat(RefNames.refsDraftComments(changeId, accountId))
        .isEqualTo("refs/draft-comments/73/67473/1011123");
  }

  @Test
  public void refsDraftCommentsPrefix() throws Exception {
    assertThat(RefNames.refsDraftCommentsPrefix(changeId))
        .isEqualTo("refs/draft-comments/73/67473/");
  }

  @Test
  public void refsStarredChanges() throws Exception {
    assertThat(RefNames.refsStarredChanges(changeId, accountId))
        .isEqualTo("refs/starred-changes/73/67473/1011123");
  }

  @Test
  public void refsStarredChangesPrefix() throws Exception {
    assertThat(RefNames.refsStarredChangesPrefix(changeId))
        .isEqualTo("refs/starred-changes/73/67473/");
  }

  @Test
  public void refsEdit() throws Exception {
    assertThat(RefNames.refsEdit(accountId, changeId, psId))
        .isEqualTo("refs/users/23/1011123/edit-67473/42");
  }

  @Test
  public void isRefsEdit() throws Exception {
    assertThat(RefNames.isRefsEdit("refs/users/23/1011123/edit-67473/42")).isTrue();

    // user ref, but no edit ref
    assertThat(RefNames.isRefsEdit("refs/users/23/1011123")).isFalse();

    // other ref
    assertThat(RefNames.isRefsEdit("refs/heads/master")).isFalse();
  }

  @Test
  public void isRefsUsers() throws Exception {
    assertThat(RefNames.isRefsUsers("refs/users/23/1011123")).isTrue();
    assertThat(RefNames.isRefsUsers("refs/users/default")).isTrue();
    assertThat(RefNames.isRefsUsers("refs/users/23/1011123/edit-67473/42")).isTrue();

    assertThat(RefNames.isRefsUsers("refs/heads/master")).isFalse();
    assertThat(RefNames.isRefsUsers("refs/groups/" + TEST_SHARDED_GROUP_UUID)).isFalse();
  }

  @Test
  public void isRefsGroups() throws Exception {
    assertThat(RefNames.isRefsGroups("refs/groups/" + TEST_SHARDED_GROUP_UUID)).isTrue();

    assertThat(RefNames.isRefsGroups("refs/heads/master")).isFalse();
    assertThat(RefNames.isRefsGroups("refs/users/23/1011123")).isFalse();
    assertThat(RefNames.isRefsGroups(RefNames.REFS_GROUPNAMES)).isFalse();
    assertThat(RefNames.isRefsGroups("refs/deleted-groups/" + TEST_SHARDED_GROUP_UUID)).isFalse();
  }

  @Test
  public void isRefsDeletedGroups() throws Exception {
    assertThat(RefNames.isRefsDeletedGroups("refs/deleted-groups/" + TEST_SHARDED_GROUP_UUID))
        .isTrue();

    assertThat(RefNames.isRefsDeletedGroups("refs/heads/master")).isFalse();
    assertThat(RefNames.isRefsDeletedGroups("refs/users/23/1011123")).isFalse();
    assertThat(RefNames.isRefsDeletedGroups(RefNames.REFS_GROUPNAMES)).isFalse();
    assertThat(RefNames.isRefsDeletedGroups("refs/groups/" + TEST_SHARDED_GROUP_UUID)).isFalse();
  }

  @Test
  public void isGroupRef() throws Exception {
    assertThat(RefNames.isGroupRef("refs/groups/" + TEST_SHARDED_GROUP_UUID)).isTrue();
    assertThat(RefNames.isGroupRef("refs/deleted-groups/" + TEST_SHARDED_GROUP_UUID)).isTrue();
    assertThat(RefNames.isGroupRef(RefNames.REFS_GROUPNAMES)).isTrue();

    assertThat(RefNames.isGroupRef("refs/heads/master")).isFalse();
    assertThat(RefNames.isGroupRef("refs/users/23/1011123")).isFalse();
  }

  @Test
  public void parseShardedRefsPart() throws Exception {
    assertThat(parseShardedRefPart("01/1")).isEqualTo(1);
    assertThat(parseShardedRefPart("01/1-drafts")).isEqualTo(1);
    assertThat(parseShardedRefPart("01/1-drafts/2")).isEqualTo(1);

    assertThat(parseShardedRefPart(null)).isNull();
    assertThat(parseShardedRefPart("")).isNull();

    // Prefix not stripped.
    assertThat(parseShardedRefPart("refs/users/01/1")).isNull();

    // Invalid characters.
    assertThat(parseShardedRefPart("01a/1")).isNull();
    assertThat(parseShardedRefPart("01/a1")).isNull();

    // Mismatched shard.
    assertThat(parseShardedRefPart("01/23")).isNull();

    // Shard too short.
    assertThat(parseShardedRefPart("1/1")).isNull();
  }

  @Test
  public void parseShardedUuidFromRefsPart() throws Exception {
    assertThat(parseShardedUuidFromRefPart(TEST_SHARDED_GROUP_UUID)).isEqualTo(TEST_GROUP_UUID);
    assertThat(parseShardedUuidFromRefPart(TEST_SHARDED_GROUP_UUID + "-2"))
        .isEqualTo(TEST_GROUP_UUID + "-2");
    assertThat(parseShardedUuidFromRefPart("7e/7ec4775d")).isEqualTo("7ec4775d");
    assertThat(parseShardedUuidFromRefPart("fo/foo")).isEqualTo("foo");

    assertThat(parseShardedUuidFromRefPart(null)).isNull();
    assertThat(parseShardedUuidFromRefPart("")).isNull();

    // Prefix not stripped.
    assertThat(parseShardedUuidFromRefPart("refs/groups/" + TEST_SHARDED_GROUP_UUID)).isNull();

    // Invalid shards.
    assertThat(parseShardedUuidFromRefPart("c/" + TEST_GROUP_UUID)).isNull();
    assertThat(parseShardedUuidFromRefPart("cca/" + TEST_GROUP_UUID)).isNull();

    // Mismatched shard.
    assertThat(parseShardedUuidFromRefPart("ca/" + TEST_GROUP_UUID)).isNull();
    assertThat(parseShardedUuidFromRefPart("64/" + TEST_GROUP_UUID)).isNull();

    // Wrong number of segments.
    assertThat(parseShardedUuidFromRefPart("cc")).isNull();
    assertThat(parseShardedUuidFromRefPart(TEST_SHARDED_GROUP_UUID + "/1")).isNull();
  }

  @Test
  public void skipShardedRefsPart() throws Exception {
    assertThat(skipShardedRefPart("01/1")).isEqualTo("");
    assertThat(skipShardedRefPart("01/1/")).isEqualTo("/");
    assertThat(skipShardedRefPart("01/1/2")).isEqualTo("/2");
    assertThat(skipShardedRefPart("01/1-edit")).isEqualTo("-edit");

    assertThat(skipShardedRefPart(null)).isNull();
    assertThat(skipShardedRefPart("")).isNull();

    // Prefix not stripped.
    assertThat(skipShardedRefPart("refs/draft-comments/01/1/2")).isNull();

    // Invalid characters.
    assertThat(skipShardedRefPart("01a/1/2")).isNull();
    assertThat(skipShardedRefPart("01a/a1/2")).isNull();

    // Mismatched shard.
    assertThat(skipShardedRefPart("01/23/2")).isNull();

    // Shard too short.
    assertThat(skipShardedRefPart("1/1")).isNull();
  }

  @Test
  public void parseAfterShardedRefsPart() throws Exception {
    assertThat(parseAfterShardedRefPart("01/1/2")).isEqualTo(2);
    assertThat(parseAfterShardedRefPart("01/1/2/4")).isEqualTo(2);
    assertThat(parseAfterShardedRefPart("01/1/2-edit")).isEqualTo(2);

    assertThat(parseAfterShardedRefPart(null)).isNull();
    assertThat(parseAfterShardedRefPart("")).isNull();

    // No ID after sharded ref part
    assertThat(parseAfterShardedRefPart("01/1")).isNull();
    assertThat(parseAfterShardedRefPart("01/1/")).isNull();
    assertThat(parseAfterShardedRefPart("01/1/a")).isNull();

    // Prefix not stripped.
    assertThat(parseAfterShardedRefPart("refs/draft-comments/01/1/2")).isNull();

    // Invalid characters.
    assertThat(parseAfterShardedRefPart("01a/1/2")).isNull();
    assertThat(parseAfterShardedRefPart("01a/a1/2")).isNull();

    // Mismatched shard.
    assertThat(parseAfterShardedRefPart("01/23/2")).isNull();

    // Shard too short.
    assertThat(parseAfterShardedRefPart("1/1")).isNull();
  }

  @Test
  public void testParseRefSuffix() throws Exception {
    assertThat(parseRefSuffix("1/2/34")).isEqualTo(34);
    assertThat(parseRefSuffix("/34")).isEqualTo(34);

    assertThat(parseRefSuffix(null)).isNull();
    assertThat(parseRefSuffix("")).isNull();
    assertThat(parseRefSuffix("34")).isNull();
    assertThat(parseRefSuffix("12/ab")).isNull();
    assertThat(parseRefSuffix("12/a4")).isNull();
    assertThat(parseRefSuffix("12/4a")).isNull();
    assertThat(parseRefSuffix("a4")).isNull();
    assertThat(parseRefSuffix("4a")).isNull();
  }

  @Test
  public void shard() throws Exception {
    assertThat(RefNames.shard(1011123)).isEqualTo("23/1011123");
    assertThat(RefNames.shard(537)).isEqualTo("37/537");
    assertThat(RefNames.shard(12)).isEqualTo("12/12");
    assertThat(RefNames.shard(0)).isEqualTo("00/0");
    assertThat(RefNames.shard(1)).isEqualTo("01/1");
    assertThat(RefNames.shard(-1)).isNull();
  }
}
