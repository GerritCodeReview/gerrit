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

package com.google.gerrit.server.project;

import static com.google.gerrit.common.data.Permission.OWNER;
import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertNotNull;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
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
import java.io.IOException;
import java.util.ArrayList;
import org.easymock.EasyMockSupport;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetReflog}. */
public class GetReflogTest extends EasyMockSupport {
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
  @Inject protected GerritApi gerritApi;

  private GitRepositoryManager repoManagerMock = createNiceMock(GitRepositoryManager.class);
  private ReflogReader reflogReaderMock = createNiceMock(ReflogReader.class);
  private Repository repositoryMock = createNiceMock(Repository.class);

  private LifecycleManager lifecycle;
  private ReviewDb reviewDb;
  private TestRepository<InMemoryRepository> repo;
  private BranchInfo repoBranchInfo;
  private ProjectConfig project;
  private IdentifiedUser regularUser;
  private IdentifiedUser reflogUser;
  private IdentifiedUser ownerUser;
  private IdentifiedUser adminUser;

  @Before
  public void setUp() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    reviewDb = schemaFactory.open();
    schemaCreator.create(reviewDb);
    adminUser = createUser("admin");
    setCurrentUser(adminUser);

    regularUser = createUser("user");
    ownerUser = createUser("owner");
    reflogUser = createUser("reflogUser");

    GroupApi projectOwnerGroup = gerritApi.groups().create("TestProjectOwners");
    projectOwnerGroup.addMembers(ownerUser.getAccountId().toString());
    AccountGroup.UUID projectOwnerGroupId = new AccountGroup.UUID(projectOwnerGroup.get().id);

    GroupApi reflogGroup = gerritApi.groups().create("TestReflogGroup");
    reflogGroup.addMembers(reflogUser.getAccountId().toString());
    AccountGroup.UUID reflogGroupId = new AccountGroup.UUID(reflogGroup.get().id);

    Project.NameKey name = new Project.NameKey("project");
    InMemoryRepository inMemoryRepo = repoManager.createRepository(name);
    project = new ProjectConfig(name);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);
    ObjectId id = repo.branch("master").commit().create();
    repoBranchInfo = new BranchInfo();
    repoBranchInfo.ref = "refs/heads/master";
    repoBranchInfo.revision = id.getName();

    setUpPermissions();
    allow(project, READ, REGISTERED_USERS, "refs/*", false);
    allow(
        project,
        READ,
        reflogGroupId,
        "refs/heads/master",
        true); // Grant exclusive access to master for reflogGroup
    allow(project, OWNER, projectOwnerGroupId, "refs/*", false);

    // InMemory repository doesn't support refLog, need to mock it
    expect(repoManagerMock.openRepository(name)).andReturn(repositoryMock).anyTimes();
    replay(repoManagerMock);
    expect(repositoryMock.getReflogReader(repoBranchInfo.ref))
        .andReturn(reflogReaderMock)
        .anyTimes();
    replay(repositoryMock);
    expect(reflogReaderMock.getReverseEntries()).andReturn(new ArrayList<ReflogEntry>()).anyTimes();
    replay(reflogReaderMock);
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
    if (reviewDb != null) {
      reviewDb.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test(expected = AuthException.class)
  public void regularUserShouldNotBeAllowedToGetReflog() throws Exception {
    GetReflog getReflog = new GetReflog(repoManagerMock);

    getReflog.apply(new BranchResource(newProjectControl(regularUser), repoBranchInfo));
  }

  @Test
  public void userWithReadAccessShouldGetReflog() throws Exception {
    GetReflog getReflog = new GetReflog(repoManagerMock);

    assertNotNull(
        getReflog.apply(new BranchResource(newProjectControl(reflogUser), repoBranchInfo)));
  }

  @Test
  public void ownerUserShouldGetReflog() throws Exception {
    GetReflog getReflog = new GetReflog(repoManagerMock);

    assertNotNull(
        getReflog.apply(new BranchResource(newProjectControl(ownerUser), repoBranchInfo)));
  }

  @Test
  public void adminUserShouldGetReflog() throws Exception {
    GetReflog getReflog = new GetReflog(repoManagerMock);

    assertNotNull(
        getReflog.apply(new BranchResource(newProjectControl(adminUser), repoBranchInfo)));
  }

  private ProjectControl newProjectControl(IdentifiedUser currentUser) throws Exception {
    return projectControlFactory.controlFor(project.getName(), currentUser);
  }

  private void allow(
      ProjectConfig project, String permission, AccountGroup.UUID id, String ref, boolean exclusive)
      throws Exception {
    Util.allow(project, permission, id, ref, exclusive);
    saveProjectConfig(project);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(cfg.getName())) {
      cfg.commit(md);
    }
    projectCache.evict(cfg.getProject());
  }

  private IdentifiedUser createUser(String username) throws AccountException, IOException {
    return userFactory.create(
        accountManager.authenticate(AuthRequest.forUser(username)).getAccountId());
  }

  private void setCurrentUser(IdentifiedUser currentUser) {
    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return currentUser;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(reviewDb);
          }
        });
  }

  private void setUpPermissions() throws Exception {
    // Remove read permissions for all users besides admin, because by default
    // Anonymous user group has ALLOW READ permission in refs/*.
    // This method is idempotent, so is safe to call on every test setup.
    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    UUID admins = groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID();

    Util.allow(pc, Permission.READ, admins, "refs/*");
  }
}
