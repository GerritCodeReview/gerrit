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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.testing.GroupTestUtil;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/**
 * Checks that invalid group configurations are flagged. Since the inconsistencies are global to the
 * test server configuration, and leak from one test method into the next one, there is no way for
 * this test to not be sandboxed.
 */
@Sandboxed
@NoHttpd
public class GroupsConsistencyIT extends AbstractDaemonTest {

  @Inject protected GroupOperations groupOperations;
  private GroupInfo gAdmin;
  private GroupInfo g1;
  private GroupInfo g2;

  private static final String BOGUS_UUID = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

  @Before
  public void basicSetup() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    String name1 = groupOperations.newGroup().name("g1").create().get();
    String name2 = groupOperations.newGroup().name("g2").create().get();

    gApi.groups().id(name1).addMembers(user.fullName());
    gApi.groups().id(name2).addMembers(admin.fullName());
    gApi.groups().id(name1).addGroups(name2);

    this.g1 = gApi.groups().id(name1).detail();
    this.g2 = gApi.groups().id(name2).detail();
    this.gAdmin = gApi.groups().id("Administrators").detail();
  }

  @Test
  public void allGood() throws Exception {
    assertThat(check()).isEmpty();
  }

  @Test
  public void missingGroupNameRef() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.REFS_GROUPNAMES);
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(Result.FORCED);
    }

    assertError("refs/meta/group-names does not exist");
  }

  @Test
  public void missingGroupRef() throws Exception {

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.refsGroups(AccountGroup.uuid(g1.id)));
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(Result.FORCED);
    }

    assertError("missing as group ref");
  }

  @Test
  public void parseGroupRef() throws Exception {

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefRename ru =
          repo.renameRef(
              RefNames.refsGroups(AccountGroup.uuid(g1.id)), RefNames.REFS_GROUPS + BOGUS_UUID);
      RefUpdate.Result result = ru.rename();
      assertThat(result).isEqualTo(Result.RENAMED);
    }

    assertError("null UUID from");
  }

  @Test
  public void missingNameEntry() throws Exception {

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefRename ru =
          repo.renameRef(
              RefNames.refsGroups(AccountGroup.uuid(g1.id)),
              RefNames.refsGroups(AccountGroup.uuid(BOGUS_UUID)));
      RefUpdate.Result result = ru.rename();
      assertThat(result).isEqualTo(Result.RENAMED);
    }

    assertError("group " + BOGUS_UUID + " has no entry in name map");
  }

  @Test
  public void groupRefDoesNotParse() throws Exception {
    updateGroupFile(
        RefNames.refsGroups(AccountGroup.uuid(g1.id)),
        GroupConfig.GROUP_CONFIG_FILE,
        "[this is not valid\n");
    assertError("does not parse");
  }

  @Test
  public void nameRefDoesNotParse() throws Exception {
    updateGroupFile(
        RefNames.REFS_GROUPNAMES,
        GroupNameNotes.getNoteKey(AccountGroup.nameKey(g1.name)).getName(),
        "[this is not valid\n");
    assertError("does not parse");
  }

  @Test
  public void inconsistentName() throws Exception {
    Config cfg = new Config();
    cfg.setString("group", null, "name", "not really");
    cfg.setString("group", null, "id", "42");
    cfg.setString("group", null, "ownerGroupUuid", gAdmin.id);

    updateGroupFile(
        RefNames.refsGroups(AccountGroup.uuid(g1.id)), GroupConfig.GROUP_CONFIG_FILE, cfg.toText());
    assertError("inconsistent name");
  }

  @Test
  public void sharedGroupID() throws Exception {
    Config cfg = new Config();
    cfg.setString("group", null, "name", g1.name);
    cfg.setInt("group", null, "id", g2.groupId);
    cfg.setString("group", null, "ownerGroupUuid", gAdmin.id);

    updateGroupFile(
        RefNames.refsGroups(AccountGroup.uuid(g1.id)), GroupConfig.GROUP_CONFIG_FILE, cfg.toText());
    assertError("shared group id");
  }

  @Test
  public void unknownOwnerGroup() throws Exception {
    Config cfg = new Config();
    cfg.setString("group", null, "name", g1.name);
    cfg.setInt("group", null, "id", g1.groupId);
    cfg.setString("group", null, "ownerGroupUuid", BOGUS_UUID);

    updateGroupFile(
        RefNames.refsGroups(AccountGroup.uuid(g1.id)), GroupConfig.GROUP_CONFIG_FILE, cfg.toText());
    assertError("nonexistent owner group");
  }

  @Test
  public void nameWithoutGroupRef() throws Exception {
    String bogusName = "bogus name";
    Config config = new Config();
    config.setString("group", null, "uuid", BOGUS_UUID);
    config.setString("group", null, "name", bogusName);

    updateGroupFile(
        RefNames.REFS_GROUPNAMES,
        GroupNameNotes.getNoteKey(AccountGroup.nameKey(bogusName)).getName(),
        config.toText());
    assertError("entry missing as group ref");
  }

  @Test
  public void nonexistentMember() throws Exception {
    updateGroupFile(RefNames.refsGroups(AccountGroup.uuid(g1.id)), "members", "314159265\n");
    assertError("nonexistent member 314159265");
  }

  @Test
  public void nonexistentSubgroup() throws Exception {
    updateGroupFile(RefNames.refsGroups(AccountGroup.uuid(g1.id)), "subgroups", BOGUS_UUID + "\n");
    assertError("has nonexistent subgroup");
  }

  @Test
  public void cyclicSubgroup() throws Exception {
    updateGroupFile(RefNames.refsGroups(AccountGroup.uuid(g1.id)), "subgroups", g1.id + "\n");
    assertWarning("cycle");
  }

  private void assertError(String msg) throws Exception {
    assertConsistency(msg, ConsistencyProblemInfo.Status.ERROR);
  }

  private void assertWarning(String msg) throws Exception {
    assertConsistency(msg, ConsistencyProblemInfo.Status.WARNING);
  }

  private List<ConsistencyProblemInfo> check() throws Exception {
    ConsistencyCheckInput in = new ConsistencyCheckInput();
    in.checkGroups = new ConsistencyCheckInput.CheckGroupsInput();
    ConsistencyCheckInfo info = gApi.config().server().checkConsistency(in);
    return info.checkGroupsResult.problems;
  }

  private void assertConsistency(String msg, ConsistencyProblemInfo.Status want) throws Exception {
    List<ConsistencyProblemInfo> problems = check();

    for (ConsistencyProblemInfo i : problems) {
      if (!i.status.equals(want)) {
        continue;
      }
      if (i.message.contains(msg)) {
        return;
      }
    }

    fail(String.format("could not find %s substring '%s' in %s", want, msg, problems));
  }

  private void updateGroupFile(String refName, String fileName, String content) throws Exception {
    GroupTestUtil.updateGroupFile(
        repoManager, allUsers, serverIdent.get(), refName, fileName, content);
  }
}
