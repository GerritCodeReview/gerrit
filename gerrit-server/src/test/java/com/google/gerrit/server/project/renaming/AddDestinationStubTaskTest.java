// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project.renaming;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.contains;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.AccountProjectWatchAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.testutil.FilesystemLoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AccountGroup.class, AccountProjectWatch.class,
  Permission.class, Project.class, ProjectConfig.class, RepositoryCache.class,
  SitePaths.class})
public class AddDestinationStubTaskTest
    extends FilesystemLoggingMockingTestCase {
  private Injector injector;

  private GitRepositoryManager repoManager;
  private ReviewDb db;
  private GroupCache groupCache;
  private ProjectCache projectCache;
  private Config serverConfig;
  private SitePaths sitePaths;
  private MetaDataUpdate.User metaDataUpdateUser;

  private File gitDir;

  public void testPriorityInSecondInternalPhase() {
    replayMocks();
    Task task = createTask("dummySource", "dummyDestination");
    int priority = task.getPriority();
    assertEquals("Priority does not match", 21, priority);
  }

  public void testCarryOutEmptyLists() throws Exception {
    IMocksControl repositoryControl = createMockControl();
    repositoryControl.checkOrder(true);
    IMocksControl metaDataUpdateControl = createMockControl();
    metaDataUpdateControl.checkOrder(true);

    // link HEAD to meta/config in "dummyDestination" repo
    Repository repository = createMock(Repository.class, repositoryControl);
    expect(repoManager.createRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository);

    RefUpdate refUpdate = createMock(RefUpdate.class);
    expect(repository.updateRef("HEAD")).andReturn(refUpdate);

    refUpdate.disableRefLog();
    expectLastCall().times(0, 1);

    expect(refUpdate.link("refs/meta/config")).andReturn(Result.NEW);

    // Getting project config from commit
    MetaDataUpdate metaDataUpdate = createMock(MetaDataUpdate.class,
        metaDataUpdateControl);
    expect(metaDataUpdateUser.create(new Project.NameKey("dummyDestination")))
        .andReturn(metaDataUpdate);

    ProjectConfig projectConfig = createMock(ProjectConfig.class,
        metaDataUpdateControl);
    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(metaDataUpdate)).andReturn(projectConfig);
    projectConfig.load(metaDataUpdate);

    // Repo has been opened, so the following steps need not be ordered
    metaDataUpdateControl.checkOrder(false);

    // Clearing existing access sections
    Collection<AccessSection> accessSections = Collections.emptyList();
    expect(projectConfig.getAccessSections()).andReturn(accessSections);

    // Setting up new reference
    AccessSection refsAccessSection = createMock(AccessSection.class);
    expect(projectConfig.getAccessSection("refs/*", true))
        .andReturn(refsAccessSection);

    // Adding new permissions
    AccountGroup anonymousUsersGroup = createMock(AccountGroup.class);
    expect(groupCache.get(AccountGroup.ANONYMOUS_USERS))
        .andReturn(anonymousUsersGroup);

    GroupReference anonymousUsersReference =
        createMock(GroupReference.class);
    expect(projectConfig.resolve(anonymousUsersGroup))
        .andReturn(anonymousUsersReference);

    List<Permission> permissions = Collections.emptyList();
    expect(refsAccessSection.getPermissions()).andReturn(permissions);

    PowerMock.mockStatic(Permission.class);
    Collection<String> allNames = Collections.emptyList();
    expect(Permission.getAllNames()).andReturn(allNames);

    // Setting project description
    Project project = createMock(Project.class);
    expect(projectConfig.getProject()).andReturn(project).anyTimes();

    project.setDescription(contains("dummySource"));
    expectLastCall().anyTimes();

    // Commit new config (again ordered)
    metaDataUpdateControl.checkOrder(true);
    metaDataUpdate.setMessage(EasyMock.anyObject(String.class));

    RevCommit revCommit = createMock(RevCommit.class);
    expect(projectConfig.commit(metaDataUpdate)).andReturn(revCommit);

    repository.close();

    metaDataUpdate.close();

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testCarryOutRemovingAccessSections() throws Exception {
    IMocksControl repositoryControl = createMockControl();
    repositoryControl.checkOrder(true);
    IMocksControl metaDataUpdateControl = createMockControl();
    metaDataUpdateControl.checkOrder(true);

    // link HEAD to meta/config in "dummyDestination" repo
    Repository repository = createMock(Repository.class, repositoryControl);
    expect(repoManager.createRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository);

    RefUpdate refUpdate = createMock(RefUpdate.class);
    expect(repository.updateRef("HEAD")).andReturn(refUpdate);

    refUpdate.disableRefLog();
    expectLastCall().times(0, 1);

    expect(refUpdate.link("refs/meta/config")).andReturn(Result.NEW);

    // Getting project config from commit
    MetaDataUpdate metaDataUpdate = createMock(MetaDataUpdate.class,
        metaDataUpdateControl);
    expect(metaDataUpdateUser.create(new Project.NameKey("dummyDestination")))
        .andReturn(metaDataUpdate);

    ProjectConfig projectConfig = createMock(ProjectConfig.class,
        metaDataUpdateControl);
    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(metaDataUpdate)).andReturn(projectConfig);
    projectConfig.load(metaDataUpdate);

    // Repo has been opened, so the following steps need not be ordered
    metaDataUpdateControl.checkOrder(false);

    // Clearing existing access sections
    AccessSection accessSection1 = createMock(AccessSection.class);
    AccessSection accessSection2 = createMock(AccessSection.class);
    Collection<AccessSection> accessSections = Lists.newArrayList(
        accessSection1, accessSection2);
    expect(projectConfig.getAccessSections()).andReturn(accessSections);
    projectConfig.remove(accessSection1);
    projectConfig.remove(accessSection2);

    // Setting up new reference
    AccessSection refsAccessSection = createMock(AccessSection.class);
    expect(projectConfig.getAccessSection("refs/*", true))
        .andReturn(refsAccessSection);

    // Adding new permissions
    AccountGroup anonymousUsersGroup = createMock(AccountGroup.class);
    expect(groupCache.get(AccountGroup.ANONYMOUS_USERS))
        .andReturn(anonymousUsersGroup);

    GroupReference anonymousUsersReference =
        createMock(GroupReference.class);
    expect(projectConfig.resolve(anonymousUsersGroup))
        .andReturn(anonymousUsersReference);

    List<Permission> permissions = Collections.emptyList();
    expect(refsAccessSection.getPermissions()).andReturn(permissions);

    PowerMock.mockStatic(Permission.class);
    Collection<String> allNames = Collections.emptyList();
    expect(Permission.getAllNames()).andReturn(allNames);

    // Setting project description
    Project project = createMock(Project.class);
    expect(projectConfig.getProject()).andReturn(project).anyTimes();

    project.setDescription(contains("dummySource"));
    expectLastCall().anyTimes();

    // Commit new config (again ordered)
    metaDataUpdateControl.checkOrder(true);
    metaDataUpdate.setMessage(EasyMock.anyObject(String.class));

    RevCommit revCommit = createMock(RevCommit.class);
    expect(projectConfig.commit(metaDataUpdate)).andReturn(revCommit);

    repository.close();

    metaDataUpdate.close();

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testCarryOutRemovingPermissions() throws Exception {
    IMocksControl repositoryControl = createMockControl();
    repositoryControl.checkOrder(true);
    IMocksControl metaDataUpdateControl = createMockControl();
    metaDataUpdateControl.checkOrder(true);

    // link HEAD to meta/config in "dummyDestination" repo
    Repository repository = createMock(Repository.class, repositoryControl);
    expect(repoManager.createRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository);

    RefUpdate refUpdate = createMock(RefUpdate.class);
    expect(repository.updateRef("HEAD")).andReturn(refUpdate);

    refUpdate.disableRefLog();
    expectLastCall().times(0, 1);

    expect(refUpdate.link("refs/meta/config")).andReturn(Result.NEW);

    // Getting project config from commit
    MetaDataUpdate metaDataUpdate = createMock(MetaDataUpdate.class,
        metaDataUpdateControl);
    expect(metaDataUpdateUser.create(new Project.NameKey("dummyDestination")))
        .andReturn(metaDataUpdate);

    ProjectConfig projectConfig = createMock(ProjectConfig.class,
        metaDataUpdateControl);
    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(metaDataUpdate)).andReturn(projectConfig);
    projectConfig.load(metaDataUpdate);

    // Repo has been opened, so the following steps need not be ordered
    metaDataUpdateControl.checkOrder(false);

    // Clearing existing access sections
    Collection<AccessSection> accessSections = Collections.emptyList();
    expect(projectConfig.getAccessSections()).andReturn(accessSections);

    // Setting up new reference
    AccessSection refsAccessSection = createMock(AccessSection.class);
    expect(projectConfig.getAccessSection("refs/*", true))
        .andReturn(refsAccessSection);

    // Adding new permissions
    AccountGroup anonymousUsersGroup = createMock(AccountGroup.class);
    expect(groupCache.get(AccountGroup.ANONYMOUS_USERS))
        .andReturn(anonymousUsersGroup);

    GroupReference anonymousUsersReference =
        createMock(GroupReference.class);
    expect(projectConfig.resolve(anonymousUsersGroup))
        .andReturn(anonymousUsersReference);

    Permission permission1 = createMock(Permission.class);
    Permission permission2 = createMock(Permission.class);
    List<Permission> permissions = Lists.newArrayList(
        permission1, permission2);
    expect(refsAccessSection.getPermissions()).andReturn(permissions);
    refsAccessSection.remove(permission1);
    refsAccessSection.remove(permission2);

    PowerMock.mockStatic(Permission.class);
    Collection<String> allNames = Collections.emptyList();
    expect(Permission.getAllNames()).andReturn(allNames);

    // Setting project description
    Project project = createMock(Project.class);
    expect(projectConfig.getProject()).andReturn(project).anyTimes();

    project.setDescription(contains("dummySource"));
    expectLastCall().anyTimes();

    // Commit new config (again ordered)
    metaDataUpdateControl.checkOrder(true);
    metaDataUpdate.setMessage(EasyMock.anyObject(String.class));

    RevCommit revCommit = createMock(RevCommit.class);
    expect(projectConfig.commit(metaDataUpdate)).andReturn(revCommit);

    repository.close();

    metaDataUpdate.close();

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testCarryOutAddingPermissions() throws Exception {
    IMocksControl repositoryControl = createMockControl();
    repositoryControl.checkOrder(true);
    IMocksControl metaDataUpdateControl = createMockControl();
    metaDataUpdateControl.checkOrder(true);

    // link HEAD to meta/config in "dummyDestination" repo
    Repository repository = createMock(Repository.class, repositoryControl);
    expect(repoManager.createRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository);

    RefUpdate refUpdate = createMock(RefUpdate.class);
    expect(repository.updateRef("HEAD")).andReturn(refUpdate);

    refUpdate.disableRefLog();
    expectLastCall().times(0, 1);

    expect(refUpdate.link("refs/meta/config")).andReturn(Result.NEW);

    // Getting project config from commit
    MetaDataUpdate metaDataUpdate = createMock(MetaDataUpdate.class,
        metaDataUpdateControl);
    expect(metaDataUpdateUser.create(new Project.NameKey("dummyDestination")))
        .andReturn(metaDataUpdate);

    ProjectConfig projectConfig = createMock(ProjectConfig.class,
        metaDataUpdateControl);
    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(metaDataUpdate)).andReturn(projectConfig);
    projectConfig.load(metaDataUpdate);

    // Repo has been opened, so the following steps need not be ordered
    metaDataUpdateControl.checkOrder(false);

    // Clearing existing access sections
    Collection<AccessSection> accessSections = Collections.emptyList();
    expect(projectConfig.getAccessSections()).andReturn(accessSections);

    // Setting up new reference
    AccessSection refsAccessSection = createMock(AccessSection.class);
    expect(projectConfig.getAccessSection("refs/*", true))
        .andReturn(refsAccessSection);

    // Adding new permissions
    AccountGroup anonymousUsersGroup = createMock(AccountGroup.class);
    expect(groupCache.get(AccountGroup.ANONYMOUS_USERS))
        .andReturn(anonymousUsersGroup);

    GroupReference anonymousUsersReference =
        createMock(GroupReference.class);
    expect(projectConfig.resolve(anonymousUsersGroup))
        .andReturn(anonymousUsersReference);

    List<Permission> permissions = Collections.emptyList();
    expect(refsAccessSection.getPermissions()).andReturn(permissions);

    Permission permission1 = createMock(Permission.class);
    Permission permission2 = createMock(Permission.class);
    PowerMock.mockStatic(Permission.class);
    Collection<String> allNames =
        Lists.newArrayList("Permission1", "Permission2");
    expect(Permission.getAllNames()).andReturn(allNames);

    expect(anonymousUsersReference.getName()).andReturn("dummyAnonymous").anyTimes();

    expect(refsAccessSection.getPermission("Permission1", true))
        .andReturn(permission1);
    Capture<PermissionRule> permission1RuleCap = new Capture<PermissionRule>();
    permission1.add(capture(permission1RuleCap));


    expect(refsAccessSection.getPermission("Permission2", true))
        .andReturn(permission2);
    Capture<PermissionRule> permission2RuleCap = new Capture<PermissionRule>();
    permission2.add(capture(permission2RuleCap));

    // Setting project description
    Project project = createMock(Project.class);
    expect(projectConfig.getProject()).andReturn(project).anyTimes();

    project.setDescription(contains("dummySource"));
    expectLastCall().anyTimes();

    // Commit new config (again ordered)
    metaDataUpdateControl.checkOrder(true);
    metaDataUpdate.setMessage(EasyMock.anyObject(String.class));

    RevCommit revCommit = createMock(RevCommit.class);
    expect(projectConfig.commit(metaDataUpdate)).andReturn(revCommit);

    repository.close();

    metaDataUpdate.close();

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.carryOut();

    PermissionRule permission1Rule = permission2RuleCap.getValue();
    assertTrue("Permission1Rule is not blocking", permission1Rule.isBlock());
    assertEquals("Permission1Rule's group does not match",
        anonymousUsersReference, permission1Rule.getGroup());

    PermissionRule permission2Rule = permission2RuleCap.getValue();
    assertTrue("Permission2Rule is not blocking", permission2Rule.isBlock());
    assertEquals("Permission2Rule's group does not match",
        anonymousUsersReference, permission2Rule.getGroup());
  }

  public void testCarryOutRepositoryCreationFails() throws Exception {
    Throwable cause = new IOException("injectedFailure");
    expect(repoManager.createRepository(
        new Project.NameKey("dummyDestination")))
        .andThrow(cause);

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");

    try {
      task.carryOut();
      fail("No exception throws, although repsitory creation failed");
    } catch (ProjectRenamingFailedException e) {
      assertEquals("Cause of thrown exception does not match", cause,
          e.getCause());
    }
  }

  public void testCarryOutRepositoryClosingUponError() throws Exception {
    Throwable cause = new IOException("injectedFailure");

    IMocksControl repositoryControl = createMockControl();
    repositoryControl.checkOrder(true);

    Repository repository = createMock(Repository.class, repositoryControl);
    expect(repoManager.createRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository);

    expect(repository.updateRef("HEAD")).andThrow(cause);

    repository.close();

    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess);

    List<AccountProjectWatch> watches = Collections.emptyList();
    ResultSet<AccountProjectWatch> watchesResultSet =
        new ListResultSet<AccountProjectWatch>(watches);
    expect(accountProjectWatchAccess.byProject(
        new Project.NameKey("dummyDestination"))).andReturn(watchesResultSet);

    Repository repository2 = createMock(Repository.class);
    expect(repoManager.openRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository2);
    repository2.close();

    PowerMock.mockStatic(RepositoryCache.class);
    RepositoryCache.close(repository2);

    File repository2Dir = assertMkdirs(gitDir, "dummyDestination.dir");
    expect(repository2.getDirectory()).andReturn(repository2Dir).anyTimes();

    expect(projectCache.get(new Project.NameKey("dummyDestination")))
        .andReturn(null);

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");

    try {
      task.carryOut();
      fail("No exception throws, although repsitory creation failed");
    } catch (ProjectRenamingFailedException e) {
      assertEquals("Cause of thrown exception does not match", cause,
          e.getCause());
    }
    assertDoesNotExist(repository2Dir);
    assertExists(gitDir);
    String filenames[] = gitDir.list();
    assertEquals(gitDir.getAbsolutePath() + " is not empty", 0,
        filenames.length);
  }

  public void testRollbackEmpty() throws Exception {
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess);

    List<AccountProjectWatch> watches = Collections.emptyList();
    ResultSet<AccountProjectWatch> watchesResultSet =
        new ListResultSet<AccountProjectWatch>(watches);
    expect(accountProjectWatchAccess.byProject(
        new Project.NameKey("dummyDestination"))).andReturn(watchesResultSet);

    Repository repository2 = createMock(Repository.class);
    expect(repoManager.openRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository2);
    repository2.close();

    PowerMock.mockStatic(RepositoryCache.class);
    RepositoryCache.close(repository2);

    File repository2Dir = assertMkdirs(gitDir, "dummyDestination.dir");
    expect(repository2.getDirectory()).andReturn(repository2Dir).anyTimes();

    expect(projectCache.get(new Project.NameKey("dummyDestination")))
        .andReturn(null);

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.rollback();

    assertDoesNotExist(repository2Dir);
    assertExists(gitDir);
    String filenames[] = gitDir.list();
    assertEquals(gitDir.getAbsolutePath() + " is not empty", 0,
        filenames.length);
  }

  public void testRollbackWithWatch() throws Exception {
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess);

    AccountProjectWatch watch = createMock(AccountProjectWatch.class);
    List<AccountProjectWatch> watches = Lists.newArrayList(watch);
    ResultSet<AccountProjectWatch> watchesResultSet =
        new ListResultSet<AccountProjectWatch>(watches);
    expect(accountProjectWatchAccess.byProject(
        new Project.NameKey("dummyDestination"))).andReturn(watchesResultSet);

    Repository repository2 = createMock(Repository.class);
    expect(repoManager.openRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository2);
    repository2.close();

    PowerMock.mockStatic(RepositoryCache.class);
    RepositoryCache.close(repository2);

    File repository2Dir = assertMkdirs(gitDir, "dummyDestination.dir");
    expect(repository2.getDirectory()).andReturn(repository2Dir).anyTimes();

    expect(projectCache.get(new Project.NameKey("dummyDestination")))
        .andReturn(null);

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.rollback();

    assertDoesNotExist(repository2Dir);
    assertExists(gitDir);
    String filenames[] = gitDir.list();
    assertEquals(gitDir.getAbsolutePath() + " is not empty", 0,
        filenames.length);

    assertLogMessageContains("dummyDestination");
  }

  public void testRollbackFilesystemCleanup() throws Exception {
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess);

    List<AccountProjectWatch> watches = Collections.emptyList();
    ResultSet<AccountProjectWatch> watchesResultSet =
        new ListResultSet<AccountProjectWatch>(watches);
    expect(accountProjectWatchAccess.byProject(
        new Project.NameKey("dummyDestination"))).andReturn(watchesResultSet);

    Repository repository2 = createMock(Repository.class);
    expect(repoManager.openRepository(
        new Project.NameKey("dummyDestination"))).andReturn(repository2);
    repository2.close();

    PowerMock.mockStatic(RepositoryCache.class);
    RepositoryCache.close(repository2);

    File dirA = assertMkdirs(gitDir, "A");
    File dirB = assertMkdirs(gitDir, "B");
    File dirBA = assertMkdirs(dirB, "A");
    File dirBB = assertMkdirs(dirB, "B"); // This dir will get cleaned up
    File dirBBA = assertMkdirs(dirBB, "A"); // This dir will get cleaned up
    File dirBC = assertMkdirs(dirB, "C");
    File dirC = assertMkdirs(gitDir, "C");

    File fileA1 = assertCreateFile(dirA, "1");

    File repository2Dir = assertMkdirs(dirBBA, "dummyDestination.dir");
    expect(repository2.getDirectory()).andReturn(repository2Dir).anyTimes();

    expect(projectCache.get(new Project.NameKey("dummyDestination")))
        .andReturn(null);

    // Done with setting up mocks
    replayMocks();

    Task task = createTask("dummySource", "dummyDestination");
    task.rollback();

    assertExists(dirA);
    assertExists(dirB);
    assertExists(dirBA);
    assertDoesNotExist(repository2Dir);
    assertDoesNotExist(dirBB);
    assertExists(dirBC);
    assertExists(dirC);
    assertExists(fileA1);

    assertExists(gitDir);
  }

  private AddDestinationStubTask createTask(String sourceName,
      String destinationName) {
    AddDestinationStubTask.Factory factory = injector.getInstance(
        AddDestinationStubTask.Factory.class);
    AddDestinationStubTask addDestinationStubTask = factory.create(
        new Project.NameKey(sourceName), new Project.NameKey(destinationName));
    return addDestinationStubTask;
  }

  private void setUpCommonExpectations() {
    expect(serverConfig.getString("gerrit", null, "basePath"))
      .andReturn(gitDir.getAbsolutePath()).anyTimes();

    expect(sitePaths.resolve(gitDir.getAbsolutePath()))
      .andReturn(gitDir.getAbsoluteFile()).anyTimes();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    gitDir = createTempDir();

    injector = Guice.createInjector(new TestModule());

    KeyUtil.setEncoderImpl(new PassThroughKeyUtilEncoder());

    setUpCommonExpectations();
  }

  private class TestModule extends TaskModule {
    @Override
    protected void configure() {
      DynamicSet.setOf(binder(), Task.Factory.class);
      taskFactory(AddDestinationStubTask.Factory.class);

      db = createMock(ReviewDb.class);
      bind(ReviewDb.class).toInstance(db);

      repoManager = createMock(GitRepositoryManager.class);
      bind(GitRepositoryManager.class).toInstance(repoManager);
      groupCache = createMock(GroupCache.class);
      bind(GroupCache.class).toInstance(groupCache);

      projectCache = createMock(ProjectCache.class);
      bind(ProjectCache.class).toInstance(projectCache);

      serverConfig = createMock(Config.class);
      bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(serverConfig);

      sitePaths = createMock(SitePaths.class);
      bind(SitePaths.class).toInstance(sitePaths);

      metaDataUpdateUser = createMock(MetaDataUpdate.User.class);
      bind(MetaDataUpdate.User.class).toInstance(metaDataUpdateUser);
    }
  }
}
