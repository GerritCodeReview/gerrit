// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo.warning;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.db.testing.GroupTestUtil;
import java.util.List;
import org.junit.Test;

public class GroupsNoteDbConsistencyCheckerTest extends AbstractGroupTest {

  @Test
  public void duplicateUUIDsInGroupNameNotes() throws Exception {
    ImmutableListMultimap<AccountGroup.UUID, String> byUUID =
        ImmutableListMultimap.of(
            new AccountGroup.UUID("uuid-1"),
            "g-1",
            new AccountGroup.UUID("uuid-2"),
            "g-2",
            new AccountGroup.UUID("uuid-1"),
            "g-3",
            new AccountGroup.UUID("uuid-2"),
            "g-4",
            new AccountGroup.UUID("uuid-1"),
            "g-5");

    ConsistencyProblemInfo exp1 =
        warning("shared group UUID 'uuid-1' between groups: g-1, g-3, g-5");
    ConsistencyProblemInfo exp2 = warning("shared group UUID 'uuid-2' between groups: g-2, g-4");

    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkGroupNameNotesConsistency(
            byUUID, ImmutableListMultimap.of());
    assertThat(problems).containsExactly(exp1, exp2);
  }

  @Test
  public void duplicateNamesInGroupNameNotes() throws Exception {
    ImmutableListMultimap<String, AccountGroup.UUID> byName =
        ImmutableListMultimap.of(
            "g-1", new AccountGroup.UUID("uuid-1"),
            "g-2", new AccountGroup.UUID("uuid-2"),
            "g-1", new AccountGroup.UUID("uuid-3"),
            "g-1", new AccountGroup.UUID("uuid-4"),
            "g-2", new AccountGroup.UUID("uuid-5"));

    ConsistencyProblemInfo exp1 =
        warning("shared group name 'g-1' between groups: uuid-1, uuid-3, uuid-4");
    ConsistencyProblemInfo exp2 = warning("shared group name 'g-2' between groups: uuid-2, uuid-5");

    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkGroupNameNotesConsistency(
            ImmutableListMultimap.of(), byName);
    assertThat(problems).containsExactly(exp1, exp2);
  }

  @Test
  public void GroupNamesRefIsMissing() throws Exception {
    checkConsistency(warning("ref %s does not exist", RefNames.REFS_GROUPNAMES));
  }

  @Test
  public void GroupNameNoteDataBlobIsMissing() throws Exception {
    GroupTestUtil.updateGroupFile(allUsersRepo, serverIdent, RefNames.REFS_GROUPNAMES, "a", "c");
    checkConsistency(warning("Group with name 'g-1' doesn't exist in the list of all names"));
  }

  @Test
  public void GroupNameNoteHasDifferentUUID() throws Exception {
    updateGroupNamesRef("[group]\n\tuuid = uuid-2\n\tname = g-1\n");
    checkConsistency(
        warning(
            "group with name 'g-1' has UUID 'uuid-1' in 'group.config' while 'uuid-2' in group name notes"));
  }

  @Test
  public void GroupNameNoteHasDifferentName() throws Exception {
    updateGroupNamesRef("[group]\n\tuuid = uuid-1\n\tname = g-2\n");
    checkConsistency(
        warning(
            "group with UUID 'uuid-1' has name 'g-1' in 'group.config' while 'g-2' in group name notes"));
  }

  @Test
  public void GroupNameNoteHasDifferentNameAndUUID() throws Exception {
    updateGroupNamesRef("[group]\n\tuuid = uuid-2\n\tname = g-2\n");
    checkConsistency(
        warning(
            "group with name 'g-1' has UUID 'uuid-1' in 'group.config' while 'uuid-2' in group name notes"),
        warning(
            "group with UUID 'uuid-1' has name 'g-1' in 'group.config' while 'g-2' in group name notes"));
  }

  @Test
  public void GroupNameNoteFailToParse() throws Exception {
    updateGroupNamesRef("[invalid");
    checkConsistency(warning("fail to check consistency with group name notes"));
  }

  private void checkConsistency(ConsistencyProblemInfo... exps) throws Exception {
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, "g-1", new AccountGroup.UUID("uuid-1"));
    assertThat(problems).containsExactly(exps);
  }

  private void updateGroupNamesRef(String content) throws Exception {
    String nameKey = GroupNameNotes.getNoteKey(new AccountGroup.NameKey("g-1")).getName();
    GroupTestUtil.updateGroupFile(
        allUsersRepo, serverIdent, RefNames.REFS_GROUPNAMES, nameKey, content);
  }
}
