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

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.db.testing.GroupTestUtil;
import java.util.List;
import org.junit.Test;

public class GroupsNoteDbConsistencyCheckerTest extends AbstractGroupTest {

  @Test
  public void groupNamesRefIsMissing() throws Exception {
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems)
        .containsExactly(warning("Group with name 'g-1' doesn't exist in the list of all names"));
  }

  @Test
  public void groupNameNoteIsMissing() throws Exception {
    updateGroupNamesRef("g-2", "[group]\n\tuuid = uuid-2\n\tname = g-2\n");
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems)
        .containsExactly(warning("Group with name 'g-1' doesn't exist in the list of all names"));
  }

  @Test
  public void groupNameNoteIsConsistent() throws Exception {
    updateGroupNamesRef("g-1", "[group]\n\tuuid = uuid-1\n\tname = g-1\n");
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems).isEmpty();
  }

  @Test
  public void groupNameNoteHasDifferentUUID() throws Exception {
    updateGroupNamesRef("g-1", "[group]\n\tuuid = uuid-2\n\tname = g-1\n");
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems)
        .containsExactly(
            warning(
                "group with name 'g-1' has UUID 'uuid-1' in 'group.config' but 'uuid-2' in group "
                    + "name notes"));
  }

  @Test
  public void groupNameNoteHasDifferentName() throws Exception {
    updateGroupNamesRef("g-1", "[group]\n\tuuid = uuid-1\n\tname = g-2\n");
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems)
        .containsExactly(warning("group note of name 'g-1' claims to represent name of 'g-2'"));
  }

  @Test
  public void groupNameNoteHasDifferentNameAndUUID() throws Exception {
    updateGroupNamesRef("g-1", "[group]\n\tuuid = uuid-2\n\tname = g-2\n");
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems)
        .containsExactly(
            warning(
                "group with name 'g-1' has UUID 'uuid-1' in 'group.config' but 'uuid-2' in group "
                    + "name notes"),
            warning("group note of name 'g-1' claims to represent name of 'g-2'"))
        .inOrder();
  }

  @Test
  public void groupNameNoteFailToParse() throws Exception {
    updateGroupNamesRef("g-1", "[invalid");
    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkWithGroupNameNotes(
            allUsersRepo, AccountGroup.nameKey("g-1"), new AccountGroup.UUID("uuid-1"));
    assertThat(problems)
        .containsExactly(
            warning(
                "fail to check consistency with group name notes: Unexpected end of config file"));
  }

  private void updateGroupNamesRef(String groupName, String content) throws Exception {
    String nameKey = GroupNameNotes.getNoteKey(AccountGroup.nameKey(groupName)).getName();
    GroupTestUtil.updateGroupFile(
        allUsersRepo, serverIdent, RefNames.REFS_GROUPNAMES, nameKey, content);
  }
}
