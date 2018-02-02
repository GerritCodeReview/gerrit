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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.testing.ConfigSuite;
import java.io.IOException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
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
    setApiUser(admin);
    replicationUser =
        accountCreator.create("replicationuser", "replicationuser@example.com", "Replication User");
    cfg.setString("receive", null, "replicationUser", replicationUser.getId().toString());
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
  public void creationOfGroupBranchIsRejectedForRegularUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    pushCommitTo(allUsersRepo, RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid")));
  }

  @Test
  @Sandboxed
  public void updateOfGroupNamesBranchIsRejectedForRegularUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    createRefInServerRepo(RefNames.REFS_GROUPNAMES);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    pushCommitTo(allUsersRepo, RefNames.REFS_GROUPNAMES);
  }

  @Test
  @Sandboxed
  public void selfReferenceDoesNotAllowRegularUsersToImpersonateReplicationUser() throws Exception {
    cfg.setString("receive", null, "replicationUser", "self");
    grantUsersWriteAccessToGroupBranches(allUsers);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    pushCommitTo(allUsersRepo, RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid")));
  }

  @Test
  @Sandboxed
  public void creationOfGroupBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result =
        pushCommitTo(allUsersRepo, RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid")));

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("Not allowed to create");
  }

  @Test
  @Sandboxed
  public void updateOfGroupBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    String groupRef = RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid"));
    createRefInServerRepo(groupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = pushCommitTo(allUsersRepo, groupRef);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("group update not allowed");
  }

  @Test
  @Sandboxed
  public void deletionOfGroupBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    String groupRef = RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid"));
    createRefInServerRepo(groupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = deleteRef(allUsersRepo, groupRef);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("group update not allowed");
  }

  @Test
  @Sandboxed
  public void creationOfGroupNamesBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    deleteRefInServerRepo(RefNames.REFS_GROUPNAMES);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = pushCommitTo(allUsersRepo, RefNames.REFS_GROUPNAMES);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication
    result.assertErrorStatus("Not allowed to create");
  }

  @Test
  @Sandboxed
  public void updateOfGroupNamesBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    createRefInServerRepo(RefNames.REFS_GROUPNAMES);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = pushCommitTo(allUsersRepo, RefNames.REFS_GROUPNAMES);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("group update not allowed");
  }

  @Test
  @Sandboxed
  public void creationOfBranchOfDeletedGroupIsAllowedForReplicationUserInSlaveMode()
      throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result =
        pushCommitTo(
            allUsersRepo, RefNames.refsDeletedGroups(new AccountGroup.UUID("deletedGroupUuid")));

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("Not allowed to create");
  }

  @Test
  @Sandboxed
  public void updateOfBranchOfDeletedGroupIsAllowedForReplicationUserInSlaveMode()
      throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    String deletedGroupRef = RefNames.refsDeletedGroups(new AccountGroup.UUID("deletedGroupUuid"));
    createRefInServerRepo(deletedGroupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = pushCommitTo(allUsersRepo, deletedGroupRef);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("group update not allowed");
  }

  @Test
  @Sandboxed
  public void deletionOfBranchOfDeletedGroupIsAllowedForReplicationUserInSlaveMode()
      throws Exception {
    grantUsersWriteAccessToGroupBranches(allUsers);
    String deletedGroupRef = RefNames.refsDeletedGroups(new AccountGroup.UUID("deletedGroupUuid"));
    createRefInServerRepo(deletedGroupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushOneCommit.Result result = deleteRef(allUsersRepo, deletedGroupRef);

    // TODO(aliceks): Adjust this part as soon as we allow group updates for the replication user.
    result.assertErrorStatus("group update not allowed");
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

  private void createRefInServerRepo(String refName) throws IOException {
    try (Repository repository = repoManager.openRepository(allUsers);
        ObjectInserter objectInserter = repository.newObjectInserter()) {
      ObjectId commitId = createEmptyCommit(objectInserter);
      RefUpdate refUpdate = repository.getRefDatabase().newUpdate(refName, false);
      refUpdate.setNewObjectId(commitId);
      refUpdate.forceUpdate();
    }
  }

  private ObjectId createEmptyCommit(ObjectInserter objectInserter) throws IOException {
    CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setMessage("New ref");
    commitBuilder.setAuthor(serverIdent.get());
    commitBuilder.setCommitter(serverIdent.get());
    commitBuilder.setTreeId(objectInserter.insert(new TreeFormatter()));
    ObjectId commitId = objectInserter.insert(commitBuilder);
    objectInserter.flush();
    return commitId;
  }

  private void deleteRefInServerRepo(String refName) throws IOException {
    try (Repository repository = repoManager.openRepository(allUsers)) {
      RefUpdate refUpdate = repository.getRefDatabase().newUpdate(refName, false);
      refUpdate.setForceUpdate(true);
      refUpdate.delete();
    }
  }

  private PushOneCommit.Result pushCommitTo(
      TestRepository<InMemoryRepository> repo, String groupRefName) throws Exception {
    PushOneCommit pushOneCommit =
        pushFactory.create(
            db, currentUser.getIdent(), repo, "Update group", "arbitraryFile.txt", "some content");
    pushOneCommit.setForce(true);
    return pushOneCommit.to(groupRefName);
  }

  private PushOneCommit.Result deleteRef(TestRepository<InMemoryRepository> repo, String refName)
      throws Exception {
    PushOneCommit pushOneCommit = pushFactory.create(db, currentUser.getIdent(), repo);
    pushOneCommit.setForce(true);
    return pushOneCommit.rm(refName);
  }
}
