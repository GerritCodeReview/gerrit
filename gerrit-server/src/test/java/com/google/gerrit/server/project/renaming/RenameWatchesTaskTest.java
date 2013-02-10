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
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.AccountProjectWatchAccess;
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
@PrepareForTest({AccountProjectWatch.class})
public class RenameWatchesTaskTest extends LoggingMockingTestCase {
  private Injector injector;

  private ReviewDb db;

  public void testNoWatcher() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess)
        .anyTimes();

    List<AccountProjectWatch> watches = new ArrayList<AccountProjectWatch>();
    expect(accountProjectWatchAccess.byProject(
        eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<AccountProjectWatch>(watches)).anyTimes();

    accountProjectWatchAccess.delete(new ArrayList<AccountProjectWatch>());
    EasyMock.expectLastCall().anyTimes();
    accountProjectWatchAccess.insert(new ArrayList<AccountProjectWatch>());
    EasyMock.expectLastCall().anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameWatchesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testSingleWatcher() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class, control);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess)
        .anyTimes();

    List<AccountProjectWatch> watches = new ArrayList<AccountProjectWatch>();
    AccountProjectWatch watch1 = createMock(AccountProjectWatch.class,
        control);
    assertTrue("Adding watch1 failed", watches.add(watch1));

    expect(accountProjectWatchAccess.byProject(
        eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<AccountProjectWatch>(watches)).anyTimes();
    control.checkOrder(true);
    accountProjectWatchAccess.delete(Lists.newArrayList(watch1));
    watch1.setProjectNameKey(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();
    accountProjectWatchAccess.insert(Lists.newArrayList(watch1));

    // Done with setting up mocks
    replayMocks();

    RenameWatchesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testMultipleWatcher() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class, control);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess)
        .anyTimes();

    List<AccountProjectWatch> watches = new ArrayList<AccountProjectWatch>();
    AccountProjectWatch watch1 = createMock(AccountProjectWatch.class,
        control);
    assertTrue("Adding watch1 failed", watches.add(watch1));
    AccountProjectWatch watch2 = createMock(AccountProjectWatch.class,
        control);
    assertTrue("Adding watch2 failed", watches.add(watch2));

    expect(accountProjectWatchAccess.byProject(
        eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<AccountProjectWatch>(watches)).anyTimes();
    control.checkOrder(true);
    accountProjectWatchAccess.delete(setEq(Lists.newArrayList(
        watch1, watch2)));
    control.checkOrder(false);
    watch1.setProjectNameKey(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();
    watch2.setProjectNameKey(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();
    control.checkOrder(true);
    accountProjectWatchAccess.insert(setEq(Lists.newArrayList(
        watch1, watch2)));

    // Done with setting up mocks
    replayMocks();

    RenameWatchesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testMultipleWatcherRollback() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);
    AccountProjectWatchAccess accountProjectWatchAccess =
        createMock(AccountProjectWatchAccess.class, control);
    expect(db.accountProjectWatches()).andReturn(accountProjectWatchAccess)
        .anyTimes();

    List<AccountProjectWatch> watches = new ArrayList<AccountProjectWatch>();
    AccountProjectWatch watch1 = createMock(AccountProjectWatch.class,
        control);
    assertTrue("Adding watch1 failed", watches.add(watch1));
    AccountProjectWatch watch2 = createMock(AccountProjectWatch.class,
        control);
    assertTrue("Adding watch2 failed", watches.add(watch2));

    // Updating dummySource -> dummyDestination
    expect(accountProjectWatchAccess.byProject(
        eq(new Project.NameKey("dummySource"))))
        .andReturn(new ListResultSet<AccountProjectWatch>(watches)).anyTimes();
    control.checkOrder(true);
    accountProjectWatchAccess.delete(setEq(Lists.newArrayList(
        watch1, watch2)));
    control.checkOrder(false);
    watch1.setProjectNameKey(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();
    watch2.setProjectNameKey(new Project.NameKey("dummyDestination"));
    EasyMock.expectLastCall().atLeastOnce();
    control.checkOrder(true);
    accountProjectWatchAccess.insert(setEq(Lists.newArrayList(
        watch1, watch2)));

    // Rollback dummyDestination -> dummySource
    expect(accountProjectWatchAccess.byProject(
        eq(new Project.NameKey("dummyDestination"))))
        .andReturn(new ListResultSet<AccountProjectWatch>(watches)).anyTimes();
    control.checkOrder(true);
    accountProjectWatchAccess.delete(setEq(Lists.newArrayList(
        watch1, watch2)));
    control.checkOrder(false);
    watch1.setProjectNameKey(new Project.NameKey("dummySource"));
    EasyMock.expectLastCall().atLeastOnce();
    watch2.setProjectNameKey(new Project.NameKey("dummySource"));
    EasyMock.expectLastCall().atLeastOnce();
    control.checkOrder(true);
    accountProjectWatchAccess.insert(setEq(Lists.newArrayList(
        watch1, watch2)));

    // Done with setting up mocks
    replayMocks();

    RenameWatchesTask task = createTask("dummySource", "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  private RenameWatchesTask createTask(String sourceName,
      String destinationName) {
    RenameWatchesTask.Factory factory = injector.getInstance(
        RenameWatchesTask.Factory.class);
    RenameWatchesTask renameWatchesTask = factory.create(
        new Project.NameKey(sourceName), new Project.NameKey(destinationName));
    return renameWatchesTask;
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
      taskFactory(RenameWatchesTask.Factory.class);

      db = createMock(ReviewDb.class);
      bind(ReviewDb.class).toInstance(db);
    }
  }
}
