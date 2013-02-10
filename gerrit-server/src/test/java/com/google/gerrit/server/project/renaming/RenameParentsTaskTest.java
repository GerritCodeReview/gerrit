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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.eq;

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.testutil.LoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.EasyMock;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Project.class, ProjectConfig.class})
public class RenameParentsTaskTest extends LoggingMockingTestCase {
  private Injector injector;
  private GitRepositoryManager repoManager;
  private MetaDataUpdate.Server metaDataUpdateServer;
  private ProjectCache projectCache;
  private IdentifiedUser identifiedUser;

  public void testNoProjects() throws ProjectRenamingFailedException {
    expect(projectCache.all()).andReturn(new ArrayList<Project.NameKey>())
        .anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  public void testProjectWithoutParent() throws ProjectRenamingFailedException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("projectA"))).anyTimes();

    // projectA -- parent: None
    Project projectA = createMock(Project.class);
    expect(projectA.getParent()).andReturn(null).anyTimes();

    ProjectState projectAState = createMock(ProjectState.class);
    expect(projectAState.getProject()).andReturn(projectA).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("projectA"))))
        .andReturn(projectAState).anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  public void testProjectWithoutMatch() throws ProjectRenamingFailedException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("projectA"),
        new Project.NameKey("projectB"))).anyTimes();

    // projectA -- parent: None
    Project projectA = createMock(Project.class);
    expect(projectA.getParent()).andReturn(null).anyTimes();

    ProjectState projectAState = createMock(ProjectState.class);
    expect(projectAState.getProject()).andReturn(projectA).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("projectA"))))
        .andReturn(projectAState).anyTimes();

    // projectB -- parent: projectA
    Project projectB = createMock(Project.class);
    expect(projectB.getParent()).andReturn(new Project.NameKey("projectA"))
        .anyTimes();

    ProjectState projectBState = createMock(ProjectState.class);
    expect(projectBState.getProject()).andReturn(projectB).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("projectB"))))
        .andReturn(projectBState).anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  public void testAbortOnError() throws ConfigInvalidException, IOException,
      RepositoryNotFoundException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("dummySource"),
        new Project.NameKey("child"))).anyTimes();

    // dummySource -- parent: None
    Project dummySource = createMock(Project.class);
    expect(dummySource.getParent()).andReturn(null).anyTimes();

    ProjectState dummySourceState = createMock(ProjectState.class);
    expect(dummySourceState.getProject()).andReturn(dummySource).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("dummySource"))))
        .andReturn(dummySourceState).anyTimes();

    // child -- parent: dummySource
    Project child = createMock(Project.class);
    expect(child.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();
    child.setParentName(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();

    ProjectState childState = createMock(ProjectState.class);
    expect(childState.getProject()).andReturn(child).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child"))))
        .andReturn(childState).anyTimes();

    // child's update
    Exception exception = new RuntimeException("Injected Failure");
    MetaDataUpdate childMetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child")))
        .andReturn(childMetaDataUpdate);
    childMetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().andThrow(exception);
    childMetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().anyTimes();
    childMetaDataUpdate.close();
    // no commit expected

    ProjectConfig childConfig = PowerMock.createMock(ProjectConfig.class);
    expect(childConfig.getProject()).andReturn(child).anyTimes();

    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(childMetaDataUpdate)).andReturn(childConfig);

    projectCache.evict(child);

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    try {
      task.carryOut();
      fail("carryOut did not throw the expected exception");
    } catch (ProjectRenamingFailedException e) {
    }
  }

  public void testProjectSingleMatch() throws ConfigInvalidException,
      IOException, ProjectRenamingFailedException,
      RepositoryNotFoundException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("dummySource"),
        new Project.NameKey("child"))).anyTimes();

    // dummySource -- parent: None
    Project dummySource = createMock(Project.class);
    expect(dummySource.getParent()).andReturn(null).anyTimes();

    ProjectState dummySourceState = createMock(ProjectState.class);
    expect(dummySourceState.getProject()).andReturn(dummySource).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("dummySource"))))
        .andReturn(dummySourceState).anyTimes();

    // child -- parent: dummySource
    Project child = createMock(Project.class);
    expect(child.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();

    ProjectState childState = createMock(ProjectState.class);
    expect(childState.getProject()).andReturn(child).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child"))))
        .andReturn(childState).anyTimes();

    // child's update
    child.setParentName(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();

    MetaDataUpdate childMetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child")))
        .andReturn(childMetaDataUpdate);
    childMetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().anyTimes();
    childMetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().atLeastOnce();
    childMetaDataUpdate.close();

    ProjectConfig childConfig = PowerMock.createMock(ProjectConfig.class);
    expect(childConfig.getProject()).andReturn(child).anyTimes();
    expect(childConfig.commit(childMetaDataUpdate)).andReturn(null);

    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(childMetaDataUpdate)).andReturn(childConfig);

    projectCache.evict(child);

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
  }

  public void testProjectSingleMatchRollback() throws ConfigInvalidException,
      IOException, ProjectRenamingFailedException,
      RepositoryNotFoundException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("dummySource"),
        new Project.NameKey("child"))).anyTimes();

    // dummySource -- parent: None
    Project dummySource = createMock(Project.class);
    expect(dummySource.getParent()).andReturn(null).anyTimes();

    ProjectState dummySourceState = createMock(ProjectState.class);
    expect(dummySourceState.getProject()).andReturn(dummySource).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("dummySource"))))
        .andReturn(dummySourceState).anyTimes();

    // child -- parent: dummySource
    Project child = createMock(Project.class);
    EasyMock.resetToStrict(child);
    expect(child.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();
    child.setParentName(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();
    expect(child.getParent()).andReturn(new Project.NameKey("dummyDestination"))
    .anyTimes();

    ProjectState childState = createMock(ProjectState.class);
    expect(childState.getProject()).andReturn(child).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child"))))
        .andReturn(childState).anyTimes();

    // child's update + rollback
    child.setParentName(new Project.NameKey("dummySource"));
    EasyMock.expectLastCall().atLeastOnce();

    MetaDataUpdate childMetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child")))
        .andReturn(childMetaDataUpdate).times(2);
    childMetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().anyTimes();
    childMetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().atLeastOnce();
    childMetaDataUpdate.close();
    EasyMock.expectLastCall().times(2);

    ProjectConfig childConfig = PowerMock.createMock(ProjectConfig.class);
    expect(childConfig.getProject()).andReturn(child).anyTimes();
    expect(childConfig.commit(childMetaDataUpdate)).andReturn(null).times(2);

    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(childMetaDataUpdate)).andReturn(childConfig)
        .times(2);

    projectCache.evict(child);
    EasyMock.expectLastCall().times(2);

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  public void testProjectMultipleMatches() throws ConfigInvalidException,
      IOException, ProjectRenamingFailedException,
      RepositoryNotFoundException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("child1"),
        new Project.NameKey("dummySource"),
        new Project.NameKey("child2"),
        new Project.NameKey("otherProject")
        )).anyTimes();

    // dummySource -- parent: None
    Project dummySource = createMock(Project.class);
    expect(dummySource.getParent()).andReturn(null).anyTimes();

    ProjectState dummySourceState = createMock(ProjectState.class);
    expect(dummySourceState.getProject()).andReturn(dummySource).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("dummySource"))))
        .andReturn(dummySourceState).anyTimes();

    // child1 -- parent: dummySource
    Project child1 = createMock(Project.class);
    expect(child1.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();

    ProjectState child1State = createMock(ProjectState.class);
    expect(child1State.getProject()).andReturn(child1).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child1"))))
        .andReturn(child1State).anyTimes();

    // child1's update
    child1.setParentName(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();

    MetaDataUpdate child1MetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child1")))
        .andReturn(child1MetaDataUpdate);
    child1MetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().anyTimes();
    child1MetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().atLeastOnce();
    child1MetaDataUpdate.close();

    ProjectConfig child1Config = PowerMock.createMock(ProjectConfig.class);
    expect(child1Config.getProject()).andReturn(child1).anyTimes();
    expect(child1Config.commit(child1MetaDataUpdate)).andReturn(null);

    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(child1MetaDataUpdate)).andReturn(child1Config);

    projectCache.evict(child1);

    // child2 -- parent: dummySource
    Project child2 = createMock(Project.class);
    expect(child2.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();

    ProjectState child2State = createMock(ProjectState.class);
    expect(child2State.getProject()).andReturn(child2).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child2"))))
        .andReturn(child2State).anyTimes();

    // child2's update
    child2.setParentName(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();

    MetaDataUpdate child2MetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child2")))
        .andReturn(child2MetaDataUpdate);
    child2MetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().anyTimes();
    child2MetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().atLeastOnce();
    child2MetaDataUpdate.close();

    ProjectConfig child2Config = PowerMock.createMock(ProjectConfig.class);
    expect(child2Config.getProject()).andReturn(child2).anyTimes();
    expect(child2Config.commit(child2MetaDataUpdate)).andReturn(null);

    expect(ProjectConfig.read(child2MetaDataUpdate)).andReturn(child2Config);

    projectCache.evict(child2);

    // otherProject -- parent: child1
    Project otherProject = createMock(Project.class);
    expect(otherProject.getParent()).andReturn(new Project.NameKey("child1"))
        .anyTimes();

    ProjectState otherProjectState = createMock(ProjectState.class);
    expect(otherProjectState.getProject()).andReturn(otherProject).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("otherProject"))))
        .andReturn(otherProjectState).anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
  }

  public void testProjectMultipleMatchesPartialRollback()
      throws ConfigInvalidException, IOException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    expect(projectCache.all()).andReturn(Arrays.asList(
        new Project.NameKey("child1"),
        new Project.NameKey("dummySource"),
        new Project.NameKey("child2"),
        new Project.NameKey("otherProject")
        )).anyTimes();

    // dummySource -- parent: None
    Project dummySource = createMock(Project.class);
    expect(dummySource.getParent()).andReturn(null).anyTimes();

    ProjectState dummySourceState = createMock(ProjectState.class);
    expect(dummySourceState.getProject()).andReturn(dummySource).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("dummySource"))))
        .andReturn(dummySourceState).anyTimes();

    // child1 -- parent: dummySource
    Project child1 = createMock(Project.class);
    EasyMock.resetToStrict(child1);
    expect(child1.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();

    ProjectState child1State = createMock(ProjectState.class);
    expect(child1State.getProject()).andReturn(child1).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child1"))))
        .andReturn(child1State).anyTimes();

    // child1's update + partial rollback
    child1.setParentName(new Project.NameKey("dummyDestination"));
    expect(child1.getParent()).andReturn(
        new Project.NameKey("dummyDestination")).anyTimes();
    child1.setParentName(new Project.NameKey("dummySource"));
    EasyMock.expectLastCall().andThrow(new RuntimeException("Injected failure"));

    MetaDataUpdate child1MetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child1")))
        .andReturn(child1MetaDataUpdate).times(2);
    child1MetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().anyTimes();
    child1MetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().atLeastOnce();
    child1MetaDataUpdate.close();
    EasyMock.expectLastCall().times(2);

    ProjectConfig child1Config = PowerMock.createMock(ProjectConfig.class);
    expect(child1Config.getProject()).andReturn(child1).anyTimes();
    expect(child1Config.commit(child1MetaDataUpdate)).andReturn(null).times(1);

    PowerMock.mockStatic(ProjectConfig.class);
    expect(ProjectConfig.read(child1MetaDataUpdate)).andReturn(child1Config)
        .times(2);

    projectCache.evict(child1);
    EasyMock.expectLastCall().times(2);

    // child2 -- parent: dummySource
    Project child2 = createMock(Project.class);
    EasyMock.resetToStrict(child2);
    expect(child2.getParent()).andReturn(new Project.NameKey("dummySource"))
        .anyTimes();

    ProjectState child2State = createMock(ProjectState.class);
    expect(child2State.getProject()).andReturn(child2).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("child2"))))
        .andReturn(child2State).anyTimes();

    // child2's update + rollback
    child2.setParentName(new Project.NameKey("dummyDestination"));
    expect(child2.getParent()).andReturn(new Project.NameKey("dummyDestination"))
        .anyTimes();
    child2.setParentName(new Project.NameKey("dummySource"));
    EasyMock.expectLastCall().atLeastOnce();

    MetaDataUpdate child2MetaDataUpdate = createMock(MetaDataUpdate.class);
    expect(metaDataUpdateServer.create(new Project.NameKey("child2")))
        .andReturn(child2MetaDataUpdate).times(2);
    child2MetaDataUpdate.setMessage(EasyMock.anyObject(String.class));
    EasyMock.expectLastCall().anyTimes();
    child2MetaDataUpdate.setAuthor(identifiedUser);
    EasyMock.expectLastCall().atLeastOnce();
    child2MetaDataUpdate.close();
    EasyMock.expectLastCall().times(2);

    ProjectConfig child2Config = PowerMock.createMock(ProjectConfig.class);
    expect(child2Config.getProject()).andReturn(child2).anyTimes();
    expect(child2Config.commit(child2MetaDataUpdate)).andReturn(null).times(2);

    expect(ProjectConfig.read(child2MetaDataUpdate)).andReturn(child2Config)
        .times(2);

    projectCache.evict(child2);
    EasyMock.expectLastCall().times(2);

    // otherProject -- parent: child1
    Project otherProject = createMock(Project.class);
    expect(otherProject.getParent()).andReturn(new Project.NameKey("child1"))
        .anyTimes();

    ProjectState otherProjectState = createMock(ProjectState.class);
    expect(otherProjectState.getProject()).andReturn(otherProject).anyTimes();

    expect(projectCache.get(eq(new Project.NameKey("otherProject"))))
        .andReturn(otherProjectState).anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameParentsTask task = createTask("dummySource",
        "dummyDestination");
    task.carryOut();
    task.rollback();

    verifyMocks();

    assertLogMessageContains("child1");
  }

  private RenameParentsTask createTask(String sourceName,
      String destinationName) {
    RenameParentsTask.Factory factory = injector.getInstance(
        RenameParentsTask.Factory.class);
    RenameParentsTask renameParentsTask = factory.create(
        new Project.NameKey(sourceName), new Project.NameKey(destinationName));
    return renameParentsTask;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    injector = Guice.createInjector(new TestModule());

    KeyUtil.setEncoderImpl(new PassThroughKeyUtilEncoder());
  }

  private class TestModule extends TaskModule {
    @Override
    protected void configure() {
      DynamicSet.setOf(binder(), Task.Factory.class);
      taskFactory(RenameParentsTask.Factory.class);

      metaDataUpdateServer = createMock(MetaDataUpdate.Server.class);
      bind(MetaDataUpdate.Server.class).toInstance(metaDataUpdateServer);

      repoManager = createMock(GitRepositoryManager.class);
      bind(GitRepositoryManager.class).toInstance(repoManager);

      projectCache = createMock(ProjectCache.class);
      bind(ProjectCache.class).toInstance(projectCache);

      identifiedUser = createMock(IdentifiedUser.class);
      bind(IdentifiedUser.class).toInstance(identifiedUser);
    }
  }
}
