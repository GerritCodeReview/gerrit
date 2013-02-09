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

package com.google.gerrit.server.project;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.reset;

import com.google.common.cache.Cache;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.project.renaming.Task;
import com.google.gerrit.testutil.LoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import java.util.ArrayList;
import java.util.List;

public class RenameProjectTest extends LoggingMockingTestCase {
  private Injector injector;
  private ReviewDb db;
  private ProjectCache projectCache;
  private IdentifiedUser identifiedUser;
  private CapabilityControl capabilityControl;
  private ProjectState sourceProjectState;
  private Project sourceProject;
  private SubmoduleSubscriptionAccess submoduleAccess;

  public void testPermissionCheck() throws NameAlreadyUsedException,
      NoSuchProjectException, ProjectRenamingFailedException {
    reset(capabilityControl);
    expect(capabilityControl.canRenameProject()).andReturn(false).anyTimes();

    // Done with setting up mocks
    replayMocks();

    try {
      renameProject("dummysource", "dummydestination");
      fail("No exception thrown, although user lacks permission to rename "
          + "projects");
    } catch (PermissionDeniedException e) {
      assertTrue("PermissionDeniedException does not contain user name",
          e.getMessage().contains("dummyUserName"));
    }
  }

  public void testNonExistingSource() throws NameAlreadyUsedException,
      PermissionDeniedException, ProjectRenamingFailedException {
    reset(projectCache);
    expect(projectCache.get(new Project.NameKey("dummysource")))
        .andReturn(null).anyTimes();

    // Done with setting up mocks
    replayMocks();

    try {
      renameProject("dummysource", "dummydestination");
      fail("No exception thrown, although source project not accessible "
          + "through project cache");
    } catch (NoSuchProjectException e) {
      assertTrue("NoSuchProjectException does not contain project name",
          e.getMessage().contains("dummysource"));
    }
  }

  public void testRenamingAllProjects() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException {
    reset(sourceProjectState);
    expect(sourceProjectState.isAllProjects()).andReturn(true).anyTimes();

    // Done with setting up mocks
    replayMocks();

    try {
      renameProject("dummysource", "dummydestination");
      fail("No exception thrown, although source project is 'All Projects'");
    } catch (ProjectRenamingFailedException e) {
    }
  }

  public void testDestinationExists() throws NoSuchProjectException,
      PermissionDeniedException, ProjectRenamingFailedException {
    reset(projectCache);
    expect(projectCache.get(new Project.NameKey("dummysource")))
        .andReturn(sourceProjectState).anyTimes();
    ProjectState destinationProjectState = createMock(
        ProjectState.class);
    expect(projectCache.get(
        new Project.NameKey("dummydestination")))
        .andReturn(destinationProjectState).anyTimes();

    // Done with setting up mocks
    replayMocks();

    try {
      renameProject("dummysource", "dummydestination");
      fail("No exception thrown, although destination project exists");
    } catch (NameAlreadyUsedException e) {
      assertTrue("NameAlreadyUsedException not contain project name",
          e.getMessage().contains("dummydestination"));
    }
  }

  public void testAbortOnSubscriptionSub() throws NameAlreadyUsedException,
      NoSuchProjectException, OrmException, PermissionDeniedException,
      ProjectRenamingFailedException {
    reset(submoduleAccess);
    SubmoduleSubscription subscription = new SubmoduleSubscription(
        new Branch.NameKey(new Project.NameKey("projectA"), "branchA"),
        new Branch.NameKey(new Project.NameKey("dummysource"), "branchB"),
        "path");
    List<SubmoduleSubscription> subsAsSub =
        new ArrayList<SubmoduleSubscription>();
    assertTrue("Adding subscription failed", subsAsSub.add(subscription));
    expect(submoduleAccess.bySubmoduleProject(
        new Project.NameKey("dummysource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subsAsSub))
        .anyTimes();

    List<SubmoduleSubscription> subsAsSuper =
        new ArrayList<SubmoduleSubscription>();
    expect(submoduleAccess.bySuperProjectProject(
        new Project.NameKey("dummysource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subsAsSuper))
        .anyTimes();

    // Done with setting up mocks
    replayMocks();

    try {
      renameProject("dummysource", "dummydestination");
      fail("No exception thrown, although source project is subscribed");
    } catch (ProjectRenamingFailedException e) {
      assertTrue("ProjectRenamingFailedException does not contain project "
          + "name", e.getMessage().contains("dummysource"));
    }
  }

  public void testRenameWithoutTasks() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    projectCache.remove(sourceProject);
    projectCache.evict(new Project.NameKey("dummydestination"));

    // Done with setting up mocks
    replayMocks();

    assertEquals("dummydestination",
        renameProject("dummysource", "dummydestination"));
  }

  public void testRenameWithSingleTask() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    Task task = createMock(Task.class);
    this.registerTask(task, "dummysource", "dummydestination");
    expect(task.getPriority()).andReturn(1).anyTimes();
    task.carryOut();

    projectCache.remove(sourceProject);
    projectCache.evict(new Project.NameKey("dummydestination"));

    // Done with setting up mocks
    replayMocks();

    assertEquals("dummydestination",
        renameProject("dummysource", "dummydestination"));
  }

  public void testRenameWithMultipleTasks() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    Task taskA = createMock(Task.class);
    this.registerTask(taskA, "dummysource", "dummydestination");
    expect(taskA.getPriority()).andReturn(1).anyTimes();
    taskA.carryOut();

    Task taskB = createMock(Task.class);
    this.registerTask(taskB, "dummysource", "dummydestination");
    expect(taskB.getPriority()).andReturn(2).anyTimes();
    taskB.carryOut();

    projectCache.remove(sourceProject);
    projectCache.evict(new Project.NameKey("dummydestination"));

    // Done with setting up mocks
    replayMocks();

    assertEquals("dummydestination",
        renameProject("dummysource", "dummydestination"));
  }

  public void testPriorityRegisterIncreasing() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    Task taskA = createMock(Task.class, control);
    this.registerTask(taskA, "dummysource", "dummydestination");
    expect(taskA.getPriority()).andReturn(1).atLeastOnce();

    Task taskB = createMock(Task.class, control);
    this.registerTask(taskB, "dummysource", "dummydestination");
    expect(taskB.getPriority()).andReturn(2).atLeastOnce();

    control.checkOrder(true);
    taskA.carryOut();
    taskB.carryOut();

    projectCache.remove(sourceProject);
    projectCache.evict(new Project.NameKey("dummydestination"));

    replayMocks();

    renameProject("dummysource", "dummydestination");
  }

  public void testPriorityRegisterDecreasing() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    Task taskB = createMock(Task.class, control);
    this.registerTask(taskB, "dummysource", "dummydestination");
    expect(taskB.getPriority()).andReturn(2).atLeastOnce();

    Task taskA = createMock(Task.class, control);
    this.registerTask(taskA, "dummysource", "dummydestination");
    expect(taskA.getPriority()).andReturn(1).atLeastOnce();

    control.checkOrder(true);
    taskA.carryOut();
    taskB.carryOut();

    projectCache.remove(sourceProject);
    projectCache.evict(new Project.NameKey("dummydestination"));

    replayMocks();

    renameProject("dummysource", "dummydestination");
  }


  public void testRollbackFirstTaskFails() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    Task taskA = createMock(Task.class, control);
    this.registerTask(taskA, "dummysource", "dummydestination");
    expect(taskA.getPriority()).andReturn(1).atLeastOnce();

    Task taskB = createMock(Task.class, control);
    this.registerTask(taskB, "dummysource", "dummydestination");
    expect(taskB.getPriority()).andReturn(2).atLeastOnce();

    control.checkOrder(true);
    taskA.carryOut();
    EasyMock.expectLastCall().andThrow(new ProjectRenamingFailedException(
        "Injected failure"));

    projectCache.evict(sourceProject);

    // Done with setting up mocks
    replayMocks();

    try {
      assertEquals("dummydestination",
          renameProject("dummysource", "dummydestination"));
      fail("No exception thrown, although tasks injected failure");
    } catch (ProjectRenamingFailedException e) {
    }
  }

  public void testRollbackSecondTaskFails() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    Task taskA = createMock(Task.class, control);
    this.registerTask(taskA, "dummysource", "dummydestination");
    expect(taskA.getPriority()).andReturn(1).atLeastOnce();

    Task taskB = createMock(Task.class, control);
    this.registerTask(taskB, "dummysource", "dummydestination");
    expect(taskB.getPriority()).andReturn(2).atLeastOnce();

    control.checkOrder(true);
    taskA.carryOut();
    taskB.carryOut();
    EasyMock.expectLastCall().andThrow(new ProjectRenamingFailedException(
        "Injected failure"));
    taskA.rollback();

    projectCache.evict(sourceProject);

    // Done with setting up mocks
    replayMocks();

    try {
      assertEquals("dummydestination",
          renameProject("dummysource", "dummydestination"));
      fail("No exception thrown, although tasks injected failure");
    } catch (ProjectRenamingFailedException e) {
    }
  }

  public void testRollbackThirdTaskFailsOneRollbackFails() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    Task taskA = createMock(Task.class, control);
    this.registerTask(taskA, "dummysource", "dummydestination");
    expect(taskA.getPriority()).andReturn(1).atLeastOnce();

    Task taskB = createMock(Task.class, control);
    this.registerTask(taskB, "dummysource", "dummydestination");
    expect(taskB.getPriority()).andReturn(2).atLeastOnce();

    Task taskC = createMock(Task.class, control);
    this.registerTask(taskC, "dummysource", "dummydestination");
    expect(taskC.getPriority()).andReturn(3).atLeastOnce();

    control.checkOrder(true);
    taskA.carryOut();
    taskB.carryOut();
    taskC.carryOut();
    EasyMock.expectLastCall().andThrow(new ProjectRenamingFailedException(
        "Injected failure carryOutC"));
    taskB.rollback();
    EasyMock.expectLastCall().andThrow(new RuntimeException(
        "Injected failure rollbackB"));
    taskA.rollback();

    projectCache.evict(sourceProject);

    // Done with setting up mocks
    replayMocks();

    try {
      assertEquals("dummydestination",
          renameProject("dummysource", "dummydestination"));
      fail("No exception thrown, although tasks injected failure");
    } catch (ProjectRenamingFailedException e) {
      assertEquals("Injected failure carryOutC", e.getMessage());
    }

    // The main exception from the carryOut of the third task has been checked
    // in the try/catch block above. But we also need to check that
    // RenameProject logged the failure of the rollback of the second task.
    assertLogThrowableMessageContains("Injected failure rollbackB");
  }

  private String renameProject(String sourceName, String destinationName)
      throws NameAlreadyUsedException, NoSuchProjectException,
      PermissionDeniedException, ProjectRenamingFailedException {
    RenameProject.Factory factory = injector.getInstance(
        RenameProject.Factory.class);
    factory.create(sourceName, destinationName);
    RenameProject renameProject = factory.create(sourceName, destinationName);
    return renameProject.renameProject();
  }

  private void setupCommonMocks() throws OrmException {
    capabilityControl = createMock(CapabilityControl.class);
    sourceProjectState = createMock(ProjectState.class);
    submoduleAccess = createMock(SubmoduleSubscriptionAccess.class);

    expect(identifiedUser.getUserName()).andReturn("dummyUserName").anyTimes();
    expect(identifiedUser.getCapabilities()).andReturn(capabilityControl)
        .anyTimes();

    expect(capabilityControl.canRenameProject()).andReturn(true).anyTimes();

    expect(projectCache.get(new Project.NameKey("dummysource")))
        .andReturn(sourceProjectState).anyTimes();
    expect(projectCache.get(
        new Project.NameKey("dummydestination"))).andReturn(null).anyTimes();

    expect(sourceProjectState.isAllProjects()).andReturn(false).anyTimes();
    expect(sourceProjectState.getProject()).andReturn(sourceProject)
        .anyTimes();

    expect(db.submoduleSubscriptions()).andReturn(submoduleAccess).anyTimes();
    List<SubmoduleSubscription> subscriptions =
        new ArrayList<SubmoduleSubscription>();
    expect(submoduleAccess.bySubmoduleProject(
        new Project.NameKey("dummysource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions))
        .anyTimes();
    expect(submoduleAccess.bySuperProjectProject(
        new Project.NameKey("dummysource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions))
        .anyTimes();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    injector = Guice.createInjector(new TestModule());


    KeyUtil.setEncoderImpl(new PassThroughKeyUtilEncoder());
    sourceProject = new Project(new Project.NameKey("dummysource"));

    setupCommonMocks();
  }

  @SuppressWarnings("unchecked")
  private void registerTask(final Task task, String source,
      String destination, boolean expectCreation) {
    // Setting up factory
    Task.Factory factory = createMock(Task.Factory.class);
    expect(factory.create(new Project.NameKey(source),
        new Project.NameKey(destination))).andReturn(task);
    if (! expectCreation) {
      EasyMock.expectLastCall().times(0, 1);
    }

    // Registering factory
    Key<DynamicSet<Task.Factory>> key = (Key<DynamicSet<Task.Factory>>) Key.get(
        Types.newParameterizedType(DynamicSet.class, TypeLiteral.get(Task.Factory.class).getType()));
    DynamicSet<Task.Factory> factories = injector.getInstance(key);
    factories.add(factory);
  }

  private void registerTask(final Task task, String source,
      String destination) {
    registerTask(task, source, destination, true);
  }

  private class TestModule extends FactoryModule {
    @Override
    protected void configure() {
      DynamicMap.mapOf(binder(), new TypeLiteral<Cache<?, ?>>() {});

      db = createMock(ReviewDb.class);
      bind(ReviewDb.class).toInstance(db);

      projectCache = createMock(ProjectCache.class);
      bind(ProjectCache.class).toInstance(projectCache);

      identifiedUser = createMock(IdentifiedUser.class);
      bind(IdentifiedUser.class).toInstance(identifiedUser);

      DynamicSet.setOf(binder(), Task.Factory.class);

      factory(RenameProject.Factory.class);
    }
  }
}