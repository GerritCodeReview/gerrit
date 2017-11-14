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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupsConsistencyChecker;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class GroupsConsistencyIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbConfig() {
    Config config = new Config();
    config.setBoolean(NotesMigration.SECTION_NOTE_DB, GROUPS.key(), NotesMigration.WRITE, true);
    config.setBoolean(NotesMigration.SECTION_NOTE_DB, GROUPS.key(), NotesMigration.READ, true);
    return config;
  }

  @Inject private GroupsConsistencyChecker groupChecker;

  @Inject
  @Named("groups_byuuid")
  private LoadingCache<String, Optional<InternalGroup>> groupsByUUIDCache;

  @Before
  public void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  protected String createGroup(String name, String owner) throws Exception {
    name = name(name);
    GroupInput in = new GroupInput();
    in.name = name;
    in.ownerId = owner;
    gApi.groups().create(in);
    return name;
  }

  void assertError(String msg) throws Exception {
    assertConsistency(msg, true);
  }
  void assertWarning(String msg) throws Exception {
    assertConsistency(msg, false);
  }

  void assertConsistency(String msg, boolean error) throws Exception {
    ConsistencyProblemInfo.Status want = error ? ConsistencyProblemInfo.Status.ERROR :
        ConsistencyProblemInfo.Status.WARNING;
    List<ConsistencyProblemInfo> problems = groupChecker.check();
    for (ConsistencyProblemInfo i : problems) {
      if (!i.status.equals(want)) {
        continue;
      }
      if (i.message.contains(msg)) {
        return;
      }
    }

    fail(String.format("could not find substring '%s' in %s", msg, problems));
  }

  GroupInfo g1;
  GroupInfo g2;

  @Before
  public void basicSetup() throws Exception {
    String g1 = createGroup("g1", "Administrators");
    String g2 = createGroup("g2", "Administrators");

    gApi.groups().id(g1).addMembers("user");
    gApi.groups().id(g2).addMembers("admin");
    gApi.groups().id(g1).addGroups(g2);

    this.g1 = gApi.groups().id(g1).detail();
    this.g2 = gApi.groups().id(g2).detail();
  }

  public boolean groupsInNoteDb() {
    return cfg.getBoolean(NotesMigration.SECTION_NOTE_DB, GROUPS.key(), NotesMigration.WRITE, false);
  }

  @Test
  public void missingGroupNameRef() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.REFS_GROUPNAMES);
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(Result.FORCED);
    }

    assertError("refs/meta/group-names does not exist");
  }

  @Test
  public void allGood() throws Exception {
    assertThat(groupChecker.check()).isEmpty();
  }

  @Test
  public void missingGroupRef() throws Exception {
    assume().that(groupsInNoteDb()).isTrue();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.refsGroups(new AccountGroup.UUID(g1.id)));
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(Result.FORCED);
    }

    assertError("refs/meta/group-names does not exist");
  }


}
