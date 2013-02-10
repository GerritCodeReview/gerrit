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

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.testutil.LoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.EasyMock;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

public class RenameRepositoryTaskTest extends LoggingMockingTestCase {
  private Injector injector;

  private GitRepositoryManager repoManager;

  public void testRenaming() throws ProjectRenamingFailedException,
      RepositoryNotFoundException {
    repoManager.renameRepository(new Project.NameKey("dummySource"),
        new Project.NameKey("dummyDestination"));

    // Done with setting up mocks
    replayMocks();

    RenameRepositoryTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testRollback() throws ProjectRenamingFailedException,
      RepositoryNotFoundException {
    EasyMock.resetToStrict(repoManager);
    repoManager.renameRepository(new Project.NameKey("dummySource"),
        new Project.NameKey("dummyDestination"));
    repoManager.renameRepository(new Project.NameKey("dummyDestination"),
        new Project.NameKey("dummySource"));

    // Done with setting up mocks
    replayMocks();

    RenameRepositoryTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  public void testRollbackFailure() throws ProjectRenamingFailedException,
      RepositoryNotFoundException {
    EasyMock.resetToStrict(repoManager);
    repoManager.renameRepository(new Project.NameKey("dummySource"),
        new Project.NameKey("dummyDestination"));
    repoManager.renameRepository(new Project.NameKey("dummyDestination"),
        new Project.NameKey("dummySource"));
    EasyMock.expectLastCall().andThrow(new RuntimeException("Injected failure"));

    // Done with setting up mocks
    replayMocks();

    RenameRepositoryTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
    task.rollback();

    verifyMocks();
    assertLogMessageContains("dummySource");
  }

  private RenameRepositoryTask createTask(String sourceName,
      String destinationName) {
    RenameRepositoryTask.Factory factory = injector.getInstance(
        RenameRepositoryTask.Factory.class);
    RenameRepositoryTask renameRepositoryTask = factory.create(
        new Project.NameKey(sourceName), new Project.NameKey(destinationName));
    return renameRepositoryTask;
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
      taskFactory(RenameRepositoryTask.Factory.class);

      repoManager = createMock(GitRepositoryManager.class);
      bind(GitRepositoryManager.class).toInstance(repoManager);
    }
  }
}
