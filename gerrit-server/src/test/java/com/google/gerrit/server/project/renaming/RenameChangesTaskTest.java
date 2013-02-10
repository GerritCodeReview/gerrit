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

import static com.google.gerrit.testutil.SetMatcher.setEq;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.eq;

import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.testutil.LoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Change.class})
public class RenameChangesTaskTest extends LoggingMockingTestCase {
  private Injector injector;

  private ReviewDb db;

  public void testNoChange() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    ChangeAccess changeAccess = createMock(ChangeAccess.class);
    expect(db.changes()).andReturn(changeAccess)
        .anyTimes();

    List<Change> changes = new ArrayList<Change>();
    expect(changeAccess.byProject(eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<Change>(changes)).anyTimes();

    changeAccess.update(new ArrayList<Change>());
    EasyMock.expectLastCall().anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameChangesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testSingleChange() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    ChangeAccess changeAccess = createMock(ChangeAccess.class);
    expect(db.changes()).andReturn(changeAccess)
        .anyTimes();

    List<Change> changes = new ArrayList<Change>();
    Change change = createMock(Change.class, control);
    assertTrue("Adding change1 failed", changes.add(change));
    expect(changeAccess.byProject(eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<Change>(changes)).anyTimes();

    // Updating changes
    change.setProject(eq(new Project.NameKey("dummyDestination")));
    control.checkOrder(true);
    changeAccess.update(Lists.newArrayList(change));

    // Done with setting up mocks
    replayMocks();

    RenameChangesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testMultipleChanges() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    ChangeAccess changeAccess = createMock(ChangeAccess.class);
    expect(db.changes()).andReturn(changeAccess)
        .anyTimes();

    List<Change> changes = new ArrayList<Change>();
    Change change1 = createMock(Change.class, control);
    assertTrue("Adding change1 failed", changes.add(change1));
    Change change2 = createMock(Change.class, control);
    assertTrue("Adding change2 failed", changes.add(change2));
    expect(changeAccess.byProject(eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<Change>(changes)).anyTimes();

    // Updating changes
    change1.setProject(eq(new Project.NameKey("dummyDestination")));
    change2.setProject(eq(new Project.NameKey("dummyDestination")));
    control.checkOrder(true);
    changeAccess.update(setEq(Lists.newArrayList(change1,change2)));

    // Done with setting up mocks
    replayMocks();

    RenameChangesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testMultipleChangesRollback() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);

    ChangeAccess changeAccess = createMock(ChangeAccess.class);
    expect(db.changes()).andReturn(changeAccess)
        .anyTimes();

    List<Change> changes = new ArrayList<Change>();
    Change change1 = createMock(Change.class, control);
    assertTrue("Adding change1 failed", changes.add(change1));
    Change change2 = createMock(Change.class, control);
    assertTrue("Adding change2 failed", changes.add(change2));
    expect(changeAccess.byProject(eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<Change>(changes)).anyTimes();

    // Updating dummySource -> dummyDestination
    change1.setProject(eq(new Project.NameKey("dummyDestination")));
    change2.setProject(eq(new Project.NameKey("dummyDestination")));
    control.checkOrder(true);
    changeAccess.update(setEq(Lists.newArrayList(change1,change2)));

    // Rollback dummyDestination -> dummySource
    control.checkOrder(false);
    expect(changeAccess.byProject(eq(new Project.NameKey("dummyDestination"))))
        .andReturn(new ListResultSet<Change>(changes)).anyTimes();
    change1.setProject(eq(new Project.NameKey("dummySource")));
    change2.setProject(eq(new Project.NameKey("dummySource")));
    control.checkOrder(true);
    changeAccess.update(setEq(Lists.newArrayList(change1,change2)));

    // Done with setting up mocks
    replayMocks();

    RenameChangesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  private RenameChangesTask createTask(String sourceName,
      String destinationName) {
    RenameChangesTask.Factory factory = injector.getInstance(
        RenameChangesTask.Factory.class);
    RenameChangesTask renameChangesTask = factory.create(
        new Project.NameKey(sourceName), new Project.NameKey(destinationName));
    return renameChangesTask;
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
      taskFactory(RenameChangesTask.Factory.class);

      db = createMock(ReviewDb.class);
      bind(ReviewDb.class).toInstance(db);
    }
  }
}
