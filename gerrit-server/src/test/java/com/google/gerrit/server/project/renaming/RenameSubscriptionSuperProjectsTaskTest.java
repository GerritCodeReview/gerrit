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
import static org.easymock.EasyMock.expectLastCall;

import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.testutil.LoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.IMocksControl;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SubmoduleSubscription.class})
public class RenameSubscriptionSuperProjectsTaskTest extends LoggingMockingTestCase {
  private Injector injector;

  private ReviewDb db;

  public void testPriorityInFirstInternalPhase() {
    replayMocks();
    Task task = createTask("dummySource", "dummyDestination");
    int priority = task.getPriority();
    assertTrue("Priority " + priority + " not in [20,40)",
        20 <= priority && priority < 40);
  }

  public void testNoSubscription() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    SubmoduleSubscriptionAccess submoduleSubscriptionAccess =
        createMock(SubmoduleSubscriptionAccess.class);
    expect(db.submoduleSubscriptions()).andReturn(submoduleSubscriptionAccess)
        .anyTimes();

    List<SubmoduleSubscription> subscriptions = new ArrayList<SubmoduleSubscription>();
    expect(submoduleSubscriptionAccess.bySuperProjectProject(
        new Project.NameKey("dummySource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions)).anyTimes();

    submoduleSubscriptionAccess.delete(new ArrayList<SubmoduleSubscription>());
    expectLastCall().anyTimes();
    submoduleSubscriptionAccess.insert(new ArrayList<SubmoduleSubscription>());
    expectLastCall().anyTimes();

    // Done with setting up mocks
    replayMocks();

    RenameSubscriptionSuperProjectsTask task =
        createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testSingleSubscription() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);
    SubmoduleSubscriptionAccess submoduleSubscriptionAccess =
        createMock(SubmoduleSubscriptionAccess.class, control);
    expect(db.submoduleSubscriptions()).andReturn(submoduleSubscriptionAccess)
        .anyTimes();

    List<SubmoduleSubscription> subscriptions = new ArrayList<SubmoduleSubscription>();
    SubmoduleSubscription subscription1 = createMock(SubmoduleSubscription.class,
        control);
    assertTrue("Adding subscription1 failed", subscriptions.add(subscription1));

    expect(submoduleSubscriptionAccess.bySuperProjectProject(
        new Project.NameKey("dummySource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions)).anyTimes();
    control.checkOrder(true);
    submoduleSubscriptionAccess.delete(Lists.newArrayList(subscription1));
    subscription1.setSuperProjectProject(new Project.NameKey("dummyDestination"));
    expectLastCall().atLeastOnce();
    submoduleSubscriptionAccess.insert(Lists.newArrayList(subscription1));

    // Done with setting up mocks
    replayMocks();

    RenameSubscriptionSuperProjectsTask task =
        createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testMultipleSubscription() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);
    SubmoduleSubscriptionAccess submoduleSubscriptionAccess =
        createMock(SubmoduleSubscriptionAccess.class, control);
    expect(db.submoduleSubscriptions()).andReturn(submoduleSubscriptionAccess)
        .anyTimes();

    List<SubmoduleSubscription> subscriptions = new ArrayList<SubmoduleSubscription>();
    SubmoduleSubscription subscription1 = createMock(SubmoduleSubscription.class,
        control);
    assertTrue("Adding subscription1 failed", subscriptions.add(subscription1));
    SubmoduleSubscription subscription2 = createMock(SubmoduleSubscription.class,
        control);
    assertTrue("Adding subscription2 failed", subscriptions.add(subscription2));

    expect(submoduleSubscriptionAccess.bySuperProjectProject(
        new Project.NameKey("dummySource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions)).anyTimes();
    control.checkOrder(true);
    submoduleSubscriptionAccess.delete(setEq(Lists.newArrayList(
        subscription1, subscription2)));
    control.checkOrder(false);
    subscription1.setSuperProjectProject(new Project.NameKey("dummyDestination"));
    expectLastCall().atLeastOnce();
    subscription2.setSuperProjectProject(new Project.NameKey("dummyDestination"));
    expectLastCall().atLeastOnce();
    control.checkOrder(true);
    submoduleSubscriptionAccess.insert(setEq(Lists.newArrayList(
        subscription1, subscription2)));

    // Done with setting up mocks
    replayMocks();

    RenameSubscriptionSuperProjectsTask task =
        createTask("dummySource", "dummyDestination");
    task.carryOut();
  }

  public void testMultipleSubscriptionRollback() throws OrmException,
      ProjectRenamingFailedException, RepositoryNotFoundException {
    IMocksControl control = createMockControl();
    control.checkOrder(false);
    SubmoduleSubscriptionAccess submoduleSubscriptionAccess =
        createMock(SubmoduleSubscriptionAccess.class, control);
    expect(db.submoduleSubscriptions()).andReturn(submoduleSubscriptionAccess)
        .anyTimes();

    List<SubmoduleSubscription> subscriptions = new ArrayList<SubmoduleSubscription>();
    SubmoduleSubscription subscription1 = createMock(SubmoduleSubscription.class,
        control);
    assertTrue("Adding subscription1 failed", subscriptions.add(subscription1));
    SubmoduleSubscription subscription2 = createMock(SubmoduleSubscription.class,
        control);
    assertTrue("Adding subscription2 failed", subscriptions.add(subscription2));

    // Updating dummySource -> dummyDestination
    expect(submoduleSubscriptionAccess.bySuperProjectProject(
        new Project.NameKey("dummySource")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions)).anyTimes();
    control.checkOrder(true);
    submoduleSubscriptionAccess.delete(setEq(Lists.newArrayList(
        subscription1, subscription2)));
    control.checkOrder(false);
    subscription1.setSuperProjectProject(new Project.NameKey("dummyDestination"));
    expectLastCall().atLeastOnce();
    subscription2.setSuperProjectProject(new Project.NameKey("dummyDestination"));
    expectLastCall().atLeastOnce();
    control.checkOrder(true);
    submoduleSubscriptionAccess.insert(setEq(Lists.newArrayList(
        subscription1, subscription2)));

    // Rollback dummyDestination -> dummySource
    expect(submoduleSubscriptionAccess.bySuperProjectProject(
        new Project.NameKey("dummyDestination")))
        .andReturn(new ListResultSet<SubmoduleSubscription>(subscriptions)).anyTimes();
    control.checkOrder(true);
    submoduleSubscriptionAccess.delete(setEq(Lists.newArrayList(
        subscription1, subscription2)));
    control.checkOrder(false);
    subscription1.setSuperProjectProject(new Project.NameKey("dummySource"));
    expectLastCall().atLeastOnce();
    subscription2.setSuperProjectProject(new Project.NameKey("dummySource"));
    expectLastCall().atLeastOnce();
    control.checkOrder(true);
    submoduleSubscriptionAccess.insert(setEq(Lists.newArrayList(
        subscription1, subscription2)));

    // Done with setting up mocks
    replayMocks();

    RenameSubscriptionSuperProjectsTask task =
        createTask("dummySource", "dummyDestination");
    task.carryOut();
    task.rollback();
  }

  private RenameSubscriptionSuperProjectsTask createTask(String sourceName,
      String destinationName) {
    RenameSubscriptionSuperProjectsTask.Factory factory = injector.getInstance(
        RenameSubscriptionSuperProjectsTask.Factory.class);
    RenameSubscriptionSuperProjectsTask renameSubscriptionSuperProjectsTask =
        factory.create(new Project.NameKey(sourceName),
            new Project.NameKey(destinationName));
    return renameSubscriptionSuperProjectsTask;
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
      taskFactory(RenameSubscriptionSuperProjectsTask.Factory.class);

      db = createMock(ReviewDb.class);
      bind(ReviewDb.class).toInstance(db);
    }
  }
}
