// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.DISABLE_REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigration.READ;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigration.WRITE;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractPushReplicationEventIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbConfig() {
    Config config = new Config();
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, true);
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), READ, true);
    return config;
  }

  @ConfigSuite.Config
  public static Config disableReviewDb() {
    Config config = noteDbConfig();
    config.setBoolean(SECTION_NOTE_DB, GROUPS.key(), DISABLE_REVIEW_DB, true);
    return config;
  }

  private TestAccount currentUser;
  private TestAccount replicationUser;

  protected abstract void setProtocolUser(Context context, TestAccount user);

  protected abstract TestRepository<InMemoryRepository> createTestRepository(
      Project.NameKey project) throws Exception;

  protected TestAccount getCurrentUser() {
    return currentUser;
  }

  @Before
  public void setUp() throws Exception {
    replicationUser =
        accountCreator.create("replicationuser", "replicationuser@example.com", "Replication User");
  }

  @Override
  protected Context setApiUser(TestAccount user) {
    currentUser = user;
    Context oldContext = super.setApiUser(user);
    setProtocolUser(atrScope.get(), user);
    return oldContext;
  }

  @Test
  @Sandboxed
  public void pushToGroupNamesBranchIsRejectedForRegularUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> repo = createTestRepository(allUsers);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    pushCommitTo(repo, RefNames.REFS_GROUPNAMES);
  }

  @Test
  @Sandboxed
  public void pushToGroupNamesBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = pushCommitTo(allUsersRepo, RefNames.REFS_GROUPNAMES);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    if (groupsWrittenToNoteDb()) {
      result.assertErrorStatus("group update not allowed");
    } else {
      result.assertErrorStatus("Not allowed to create");
    }
  }

  private boolean groupsWrittenToNoteDb() {
    return cfg.getBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, false);
  }

  private void grantUsersWriteAccessToGroupBranches(Project.NameKey project) throws Exception {
    grant(project, RefNames.REFS_GROUPS + "*", Permission.CREATE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_GROUPS + "*", Permission.PUSH, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_DELETED_GROUPS + "*", Permission.CREATE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_DELETED_GROUPS + "*", Permission.PUSH, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_GROUPNAMES, Permission.PUSH, false, REGISTERED_USERS);
    grant(project, RefNames.REFS_GROUPNAMES, Permission.CREATE, false, REGISTERED_USERS);

    // RefNames.REFS_GROUPNAMES is only visible for users with ACCESS_DATABASE permission
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
  }

  private PushOneCommit.Result pushCommitTo(
      TestRepository<InMemoryRepository> repo, String groupRefName) throws Exception {
    PushOneCommit pushOneCommit =
        pushFactory.create(
            db, currentUser.getIdent(), repo, "Update group", "arbitraryFile.txt", "some content");
    // TODO(aliceks): Figure out a way to not depend on 'force'.
    pushOneCommit.setForce(true);
    return pushOneCommit.to(groupRefName);
  }
}
