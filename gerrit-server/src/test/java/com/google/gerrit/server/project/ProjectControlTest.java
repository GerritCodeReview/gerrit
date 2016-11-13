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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ProjectControl}. */
public class ProjectControlTest {
  @Inject private AccountManager accountManager;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private InMemoryDatabase schemaFactory;
  @Inject private InMemoryRepositoryManager repoManager;
  @Inject private ProjectControl.GenericFactory projectControlFactory;
  @Inject private SchemaCreator schemaCreator;
  @Inject private ThreadLocalRequestContext requestContext;
  @Inject protected ProjectCache projectCache;
  @Inject protected MetaDataUpdate.Server metaDataUpdateFactory;
  @Inject protected AllProjectsName allProjects;
  @Inject protected GroupCache groupCache;

  private LifecycleManager lifecycle;
  private ReviewDb db;
  private TestRepository<InMemoryRepository> repo;
  private ProjectConfig project;
  private IdentifiedUser user;
  private AccountGroup.UUID admins;

  @Before
  public void setUp() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    // Need to create at least one user to be admin before creating a "normal"
    // registered user.
    // See AccountManager#create().
    accountManager.authenticate(AuthRequest.forUser("admin")).getAccountId();
    admins = groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID();
    setUpPermissions();

    Account.Id userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    user = userFactory.create(userId);

    Project.NameKey name = new Project.NameKey("project");
    InMemoryRepository inMemoryRepo = repoManager.createRepository(name);
    project = new ProjectConfig(name);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);

    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });
  }

  @After
  public void tearDown() {
    if (repo != null) {
      repo.getRepository().close();
    }
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void canReadCommitWhenAllRefsVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/*");
    ObjectId id = repo.branch("master").commit().create();
    ProjectControl pc = newProjectControl();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(id)));
  }

  @Test
  public void canReadCommitIfTwoRefsVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch2");

    ObjectId id1 = repo.branch("branch1").commit().create();
    ObjectId id2 = repo.branch("branch2").commit().create();

    ProjectControl pc = newProjectControl();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(id1)));
    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(id2)));
  }

  @Test
  public void canReadCommitIfRefVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");
    deny(project, READ, REGISTERED_USERS, "refs/heads/branch2");

    ObjectId id1 = repo.branch("branch1").commit().create();
    ObjectId id2 = repo.branch("branch2").commit().create();

    ProjectControl pc = newProjectControl();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(id1)));
    assertFalse(pc.canReadCommit(db, r, rw.parseCommit(id2)));
  }

  @Test
  public void canReadCommitIfReachableFromVisibleRef() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");
    deny(project, READ, REGISTERED_USERS, "refs/heads/branch2");

    RevCommit parent1 = repo.commit().create();
    repo.branch("branch1").commit().parent(parent1).create();

    RevCommit parent2 = repo.commit().create();
    repo.branch("branch2").commit().parent(parent2).create();

    ProjectControl pc = newProjectControl();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();
    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(parent1)));
    assertFalse(pc.canReadCommit(db, r, rw.parseCommit(parent2)));
  }

  @Test
  public void cannotReadAfterRollbackWithRestrictedRead() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/heads/branch1");

    RevCommit parent1 = repo.commit().create();
    ObjectId id1 = repo.branch("branch1").commit().parent(parent1).create();

    ProjectControl pc = newProjectControl();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(parent1)));
    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(id1)));

    repo.branch("branch1").update(parent1);
    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(parent1)));
    assertFalse(pc.canReadCommit(db, r, rw.parseCommit(id1)));
  }

  @Test
  public void canReadAfterRollbackWithAllRefsVisible() throws Exception {
    allow(project, READ, REGISTERED_USERS, "refs/*");

    RevCommit parent1 = repo.commit().create();
    ObjectId id1 = repo.branch("branch1").commit().parent(parent1).create();

    ProjectControl pc = newProjectControl();
    RevWalk rw = repo.getRevWalk();
    Repository r = repo.getRepository();

    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(parent1)));
    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(id1)));

    repo.branch("branch1").update(parent1);
    assertTrue(pc.canReadCommit(db, r, rw.parseCommit(parent1)));
    assertFalse(pc.canReadCommit(db, r, rw.parseCommit(id1)));
  }

  private ProjectControl newProjectControl() throws Exception {
    return projectControlFactory.controlFor(project.getName(), user);
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
    // Remove read permissions for all users besides admin, because by default
    // Anonymous user group has ALLOW READ permission in refs/*.
    // This method is idempotent, so is safe to call on every test setup.
    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    allow(pc, Permission.READ, admins, "refs/*");
  }
}
