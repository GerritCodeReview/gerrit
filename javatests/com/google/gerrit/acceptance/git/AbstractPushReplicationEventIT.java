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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope.Context;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
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
    grantUsersWriteAccessToAllBranches(allUsers);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    ObjectId newCommit = createArbitraryRootCommit(allUsersRepo);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    updateRef(allUsersRepo, RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid")), newCommit);
  }

  @Test
  @Sandboxed
  public void updateOfGroupNamesBranchIsRejectedForRegularUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    createRefInServerRepo(allUsers, RefNames.REFS_GROUPNAMES);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    RevCommit tipCommit = fetchTipCommitOfRef(allUsersRepo, allUsers, RefNames.REFS_GROUPNAMES);
    ObjectId newCommit = createArbitraryCommit(allUsersRepo, tipCommit);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    updateRef(allUsersRepo, RefNames.REFS_GROUPNAMES, newCommit);
  }

  @Test
  @Sandboxed
  public void selfReferenceDoesNotAllowRegularUsersToImpersonateReplicationUser() throws Exception {
    cfg.setString("receive", null, "replicationUser", "self");
    grantUsersWriteAccessToAllBranches(allUsers);
    restartAsSlave();

    setApiUser(admin);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    ObjectId newCommit = createArbitraryRootCommit(allUsersRepo);
    exception.expect(TransportException.class);
    exception.expectMessage("not enabled");
    updateRef(allUsersRepo, RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid")), newCommit);
  }

  @Test
  @Sandboxed
  public void creationOfGroupBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    String groupRef = RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid"));
    ObjectId newCommit = createArbitraryRootCommit(allUsersRepo);
    PushResult pushResult = updateRef(allUsersRepo, groupRef, newCommit);

    GitUtil.assertPushOk(pushResult, groupRef);
  }

  @Test
  @Sandboxed
  public void updateOfGroupBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    String groupRef = RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid"));
    createRefInServerRepo(allUsers, groupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    RevCommit tipCommit = fetchTipCommitOfRef(allUsersRepo, allUsers, groupRef);
    ObjectId newCommit = createArbitraryCommit(allUsersRepo, tipCommit);
    PushResult pushResult = updateRef(allUsersRepo, groupRef, newCommit);

    GitUtil.assertPushOk(pushResult, groupRef);
  }

  @Test
  @Sandboxed
  public void deletionOfGroupBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    String groupRef = RefNames.refsGroups(new AccountGroup.UUID("newGroupUuid"));
    createRefInServerRepo(allUsers, groupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushResult pushResult = deleteRef(allUsersRepo, groupRef);

    GitUtil.assertPushOk(pushResult, groupRef);
  }

  @Test
  @Sandboxed
  public void creationOfGroupNamesBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    deleteRefInServerRepo(allUsers, RefNames.REFS_GROUPNAMES);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    ObjectId newCommit = createArbitraryRootCommit(allUsersRepo);
    PushResult pushResult = updateRef(allUsersRepo, RefNames.REFS_GROUPNAMES, newCommit);

    GitUtil.assertPushOk(pushResult, RefNames.REFS_GROUPNAMES);
  }

  @Test
  @Sandboxed
  public void updateOfGroupNamesBranchIsAllowedForReplicationUserInSlaveMode() throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    createRefInServerRepo(allUsers, RefNames.REFS_GROUPNAMES);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    RevCommit tipCommit = fetchTipCommitOfRef(allUsersRepo, allUsers, RefNames.REFS_GROUPNAMES);
    ObjectId newCommit = createArbitraryCommit(allUsersRepo, tipCommit);
    PushResult pushResult = updateRef(allUsersRepo, RefNames.REFS_GROUPNAMES, newCommit);

    GitUtil.assertPushOk(pushResult, RefNames.REFS_GROUPNAMES);
  }

  @Test
  @Sandboxed
  public void creationOfBranchOfDeletedGroupIsAllowedForReplicationUserInSlaveMode()
      throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    String deletedGroupRef = RefNames.refsDeletedGroups(new AccountGroup.UUID("deletedGroupUuid"));
    ObjectId newCommit = createArbitraryRootCommit(allUsersRepo);
    PushResult pushResult = updateRef(allUsersRepo, deletedGroupRef, newCommit);

    GitUtil.assertPushOk(pushResult, deletedGroupRef);
  }

  @Test
  @Sandboxed
  public void updateOfBranchOfDeletedGroupIsAllowedForReplicationUserInSlaveMode()
      throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    String deletedGroupRef = RefNames.refsDeletedGroups(new AccountGroup.UUID("deletedGroupUuid"));
    createRefInServerRepo(allUsers, deletedGroupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    RevCommit tipCommit = fetchTipCommitOfRef(allUsersRepo, allUsers, deletedGroupRef);
    ObjectId newCommit = createArbitraryCommit(allUsersRepo, tipCommit);
    PushResult pushResult = updateRef(allUsersRepo, deletedGroupRef, newCommit);

    GitUtil.assertPushOk(pushResult, deletedGroupRef);
  }

  @Test
  @Sandboxed
  public void deletionOfBranchOfDeletedGroupIsAllowedForReplicationUserInSlaveMode()
      throws Exception {
    grantUsersWriteAccessToAllBranches(allUsers);
    String deletedGroupRef = RefNames.refsDeletedGroups(new AccountGroup.UUID("deletedGroupUuid"));
    createRefInServerRepo(allUsers, deletedGroupRef);
    restartAsSlave();

    setApiUser(replicationUser);
    TestRepository<InMemoryRepository> allUsersRepo = createTestRepository(allUsers);
    PushResult pushResult = deleteRef(allUsersRepo, deletedGroupRef);

    GitUtil.assertPushOk(pushResult, deletedGroupRef);
  }

  private void grantUsersWriteAccessToAllBranches(Project.NameKey project) throws Exception {
    grant(project, RefNames.REFS + "*", Permission.CREATE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS + "*", Permission.PUSH, false, REGISTERED_USERS);
    grant(project, RefNames.REFS + "*", Permission.DELETE, false, REGISTERED_USERS);
    grant(project, RefNames.REFS + "*", Permission.FORGE_AUTHOR, false, REGISTERED_USERS);
    grant(project, RefNames.REFS + "*", Permission.FORGE_COMMITTER, false, REGISTERED_USERS);

    // RefNames.REFS_GROUPNAMES is only visible for users with ACCESS_DATABASE permission
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
  }

  private void createRefInServerRepo(Project.NameKey projectName, String refName) throws Exception {
    try (Repository repository = repoManager.openRepository(projectName);
        ObjectInserter objectInserter = repository.newObjectInserter()) {
      ObjectId commitId = createEmptyCommit(objectInserter);
      RefUpdate refUpdate = repository.getRefDatabase().newUpdate(refName, false);
      refUpdate.setNewObjectId(commitId);
      refUpdate.forceUpdate();
    }
  }

  private ObjectId createEmptyCommit(ObjectInserter objectInserter) throws Exception {
    CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setMessage("New ref");
    commitBuilder.setAuthor(serverIdent.get());
    commitBuilder.setCommitter(serverIdent.get());
    commitBuilder.setTreeId(objectInserter.insert(new TreeFormatter()));
    ObjectId commitId = objectInserter.insert(commitBuilder);
    objectInserter.flush();
    return commitId;
  }

  private void deleteRefInServerRepo(Project.NameKey projectName, String refName) throws Exception {
    try (Repository repository = repoManager.openRepository(projectName)) {
      RefUpdate refUpdate = repository.getRefDatabase().newUpdate(refName, false);
      refUpdate.setForceUpdate(true);
      refUpdate.delete();
    }
  }

  private ObjectId getTipOfRefFromServerRepo(Project.NameKey projectName, String refName)
      throws Exception {
    try (Repository repository = repoManager.openRepository(projectName)) {
      Ref ref = repository.getRefDatabase().exactRef(refName);
      return ref.getObjectId();
    }
  }

  private ObjectId createArbitraryRootCommit(TestRepository<InMemoryRepository> repo)
      throws Exception {
    return repo.commit()
        .author(serverIdent.get())
        .committer(serverIdent.get())
        .message("Replicated commit")
        .create();
  }

  private ObjectId createArbitraryCommit(
      TestRepository<InMemoryRepository> repo, RevCommit parentCommit) throws Exception {
    return repo.commit()
        .author(serverIdent.get())
        .committer(serverIdent.get())
        .message("Replicated commit")
        .parent(parentCommit)
        .create();
  }

  private static PushResult updateRef(
      TestRepository<InMemoryRepository> repo, String refName, ObjectId commitId) throws Exception {
    return GitUtil.pushOne(repo, commitId.getName(), refName, false, false, ImmutableList.of());
  }

  private static PushResult deleteRef(TestRepository<InMemoryRepository> repo, String refName)
      throws Exception {
    return GitUtil.deleteRef(repo, refName);
  }

  private RevCommit fetchTipCommitOfRef(
      TestRepository<InMemoryRepository> repo, Project.NameKey projectName, String refName)
      throws Exception {
    // The ref might not be advertised but the commits are nevertheless.
    // -> Fetch the tip commit directly.
    ObjectId tipCommitId = getTipOfRefFromServerRepo(projectName, refName);
    repo.git().fetch().setRefSpecs(tipCommitId.getName()).call();
    return repo.getRevWalk().parseCommit(tipCommitId);
  }
}
