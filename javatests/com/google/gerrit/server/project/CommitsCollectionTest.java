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

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
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
import org.junit.After;
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
  @Inject private ProjectOperations projectOperations;

  private TestRepository<InMemoryRepository> repo;
  private Project.NameKey project;

  @Before
  public void setUp() throws Exception {
    setUpPermissions();

    Account.Id user = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    testEnvironment.setApiUser(user);
    project = projectOperations.newProject().create();
    repo = new TestRepository<>(repoManager.openRepository(project));
  }

  @After
  public void tearDown() {
    repo.getRepository().close();
  }

  @Test
  public void canReadCommitWhenAllRefsVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    ObjectId id = repo.branch("master").commit().create();
    ProjectState state = readProjectState();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(commits.canRead(state, r, rw.parseCommit(id)));
  }

  @Test
  public void canReadCommitIfTwoRefsVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/branch1").group(REGISTERED_USERS))
        .add(allow(READ).ref("refs/heads/branch2").group(REGISTERED_USERS))
        .update();

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
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/branch1").group(REGISTERED_USERS))
        .add(deny(READ).ref("refs/heads/branch2").group(REGISTERED_USERS))
        .update();

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
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/branch1").group(REGISTERED_USERS))
        .add(deny(READ).ref("refs/heads/branch2").group(REGISTERED_USERS))
        .update();

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
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/branch1").group(REGISTERED_USERS))
        .update();

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
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

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
    return projectCache.get(project).get();
  }

  private void setUpPermissions() throws Exception {
    // Remove read permissions for all users besides admin, because by default
    // Anonymous user group has ALLOW READ permission in refs/*.
    // This method is idempotent, so is safe to call on every test setup.
    TestProjectUpdate.Builder u = projectOperations.allProjectsForUpdate();
    projectCache.getAllProjects().getConfig().getAccessSectionNames().stream()
        .filter(sec -> sec.startsWith(R_REFS))
        .forEach(sec -> u.remove(permissionKey(Permission.READ).ref(sec)));
    getAdmins().forEach(admin -> u.add(allow(Permission.READ).ref("refs/*").group(admin)));
    u.update();
  }

  private ImmutableList<AccountGroup.UUID> getAdmins() {
    Permission adminPermission =
        projectCache
            .getAllProjects()
            .getConfig()
            .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
            .orElseThrow(() -> new IllegalStateException("access section does not exist"))
            .getPermission(GlobalCapability.ADMINISTRATE_SERVER);

    return adminPermission.getRules().stream()
        .map(PermissionRule::getGroup)
        .map(GroupReference::getUUID)
        .collect(ImmutableList.toImmutableList());
  }
}
