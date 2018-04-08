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

package com.google.gerrit.server.project;

import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.server.restapi.project.CommitsCollection;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link CommitsCollection}. */
public class CommitsCollectionTest {
  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Inject private AccountManager accountManager;
  @Inject private InMemoryRepositoryManager repoManager;
  @Inject protected ProjectCache projectCache;
  @Inject protected MetaDataUpdate.Server metaDataUpdateFactory;
  @Inject protected AllProjectsName allProjects;
  @Inject private CommitsCollection commits;

  private TestRepository<InMemoryRepository> repo;
  private ProjectConfig project;

  @Before
  public void setUp() throws Exception {
    setUpPermissions();

    Account.Id user = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    testEnvironment.setApiUser(user);

    Project.NameKey name = new Project.NameKey("project");
    InMemoryRepository inMemoryRepo = repoManager.createRepository(name);
    project = new ProjectConfig(name);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);
  }

  @Test
  public void canReadCommitWhenAllRefsVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/*");
    ObjectId id = repo.branch("master").commit().create();
    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(commits.canRead(state, r, rw.parseCommit(id)));
  }

  @Test
  public void canReadCommitIfTwoRefsVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch2");

    ObjectId id1 = repo.branch("branch1").commit().create();
    ObjectId id2 = repo.branch("branch2").commit().create();

    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(commits.canRead(state, r, rw.parseCommit(id1)));
    assertTrue(commits.canRead(state, r, rw.parseCommit(id2)));
  }

  @Test
  public void canReadCommitIfRefVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");
    deny(project, READ, REGISTERED_USERS, "refs/heads/branch2");

    ObjectId id1 = repo.branch("branch1").commit().create();
    ObjectId id2 = repo.branch("branch2").commit().create();

    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(commits.canRead(state, r, rw.parseCommit(id1)));
    assertFalse(commits.canRead(state, r, rw.parseCommit(id2)));
  }

  @Test
  public void canReadCommitIfReachableFromVisibleRef() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");
    deny(project, READ, REGISTERED_USERS, "refs/heads/branch2");

    RevCommit parent1 = repo.commit().create();
    repo.branch("branch1").commit().parent(parent1).create();

    RevCommit parent2 = repo.commit().create();
    repo.branch("branch2").commit().parent(parent2).create();

    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();
    assertTrue(commits.canRead(state, r, rw.parseCommit(parent1)));
    assertFalse(commits.canRead(state, r, rw.parseCommit(parent2)));
  }

  @Test
  public void cannotReadAfterRollbackWithRestrictedRead() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");

    RevCommit parent1 = repo.commit().create();
    ObjectId id1 = repo.branch("branch1").commit().parent(parent1).create();

    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(commits.canRead(state, r, rw.parseCommit(parent1)));
    assertTrue(commits.canRead(state, r, rw.parseCommit(id1)));

    repo.branch("branch1").update(parent1);
    assertTrue(commits.canRead(state, r, rw.parseCommit(parent1)));
    assertFalse(commits.canRead(state, r, rw.parseCommit(id1)));
  }

  @Test
  public void canReadAfterRollbackWithAllRefsVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/*");

    RevCommit parent1 = repo.commit().create();
    ObjectId id1 = repo.branch("branch1").commit().parent(parent1).create();

    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(commits.canRead(state, r, rw.parseCommit(parent1)));
    assertTrue(commits.canRead(state, r, rw.parseCommit(id1)));

    repo.branch("branch1").update(parent1);
    assertTrue(commits.canRead(state, r, rw.parseCommit(parent1)));
    assertFalse(commits.canRead(state, r, rw.parseCommit(id1)));
  }

  private ProjectState readProjectState() throws Exception {
    return projectCache.get(project.getName());
  }

  protected void allow(ProjectConfig project, String permission, AccountGroup.UUID id, String ref)
      throws Exception {
    Util.allow(project, permission, id, ref);
    saveProjectConfig(project);
  }

  protected void deny(ProjectConfig project, String permission, AccountGroup.UUID id, String ref)
      throws Exception {
    Util.deny(project, permission, id, ref);
    saveProjectConfig(project);
  }

  protected void saveProjectConfig(ProjectConfig cfg) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(cfg.getName())) {
      cfg.commit(md);
    }
    projectCache.evict(cfg.getProject());
  }

  private void setUpPermissions() throws Exception {
    ImmutableList<AccountGroup.UUID> admins = getAdmins();

    // Remove read permissions for all users besides admin, because by default
    // Anonymous user group has ALLOW READ permission in refs/*.
    // This method is idempotent, so is safe to call on every test setup.
    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    for (AccountGroup.UUID admin : admins) {
      allow(pc, Permission.READ, admin, "refs/*");
    }
  }

  private ImmutableList<AccountGroup.UUID> getAdmins() {
    Permission adminPermission =
        projectCache
            .getAllProjects()
            .getConfig()
            .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
            .getPermission(GlobalCapability.ADMINISTRATE_SERVER);

    return adminPermission
        .getRules()
        .stream()
        .map(PermissionRule::getGroup)
        .map(GroupReference::getUUID)
        .collect(ImmutableList.toImmutableList());
  }
}
