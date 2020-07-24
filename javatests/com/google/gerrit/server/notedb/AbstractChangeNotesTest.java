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

package com.google.gerrit.server.notedb;

import static com.google.inject.Scopes.SINGLETON;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.RobotClassifier;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.DefaultUrlFormatter;
import com.google.gerrit.server.config.EnableReverseDnsLookup;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.AssertableExecutorService;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.FakeAccountCache;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.TestChanges;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Providers;
import java.sql.Timestamp;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;

@Ignore
@RunWith(ConfigSuite.class)
public abstract class AbstractChangeNotesTest {
  private static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");

  @ConfigSuite.Parameter public Config testConfig;

  protected Account.Id otherUserId;
  protected FakeAccountCache accountCache;
  protected IdentifiedUser changeOwner;
  protected IdentifiedUser otherUser;
  protected InMemoryRepository repo;
  protected InMemoryRepositoryManager repoManager;
  protected PersonIdent serverIdent;
  protected InternalUser internalUser;
  protected Project.NameKey project;
  protected RevWalk rw;
  protected TestRepository<InMemoryRepository> tr;
  protected AssertableExecutorService assertableFanOutExecutor;

  @Inject protected IdentifiedUser.GenericFactory userFactory;

  @Inject protected NoteDbUpdateManager.Factory updateManagerFactory;

  @Inject protected AllUsersName allUsers;

  @Inject protected AbstractChangeNotes.Args args;

  @Inject @GerritServerId private String serverId;

  protected Injector injector;
  private String systemTimeZone;

  @Before
  public void setUpTestEnvironment() throws Exception {
    setTimeForTesting();

    serverIdent = new PersonIdent("Gerrit Server", "noreply@gerrit.com", TimeUtil.nowTs(), TZ);
    project = Project.nameKey("test-project");
    repoManager = new InMemoryRepositoryManager();
    repo = repoManager.createRepository(project);
    tr = new TestRepository<>(repo);
    rw = tr.getRevWalk();
    accountCache = new FakeAccountCache();
    Account.Builder co = Account.builder(Account.id(1), TimeUtil.nowTs());
    co.setFullName("Change Owner");
    co.setPreferredEmail("change@owner.com");
    accountCache.put(co.build());
    Account.Builder ou = Account.builder(Account.id(2), TimeUtil.nowTs());
    ou.setFullName("Other Account");
    ou.setPreferredEmail("other@account.com");
    accountCache.put(ou.build());
    assertableFanOutExecutor = new AssertableExecutorService();

    injector =
        Guice.createInjector(
            new FactoryModule() {
              @Override
              public void configure() {
                install(new GitModule());

                install(new DefaultUrlFormatter.Module());
                install(NoteDbModule.forTest());
                bind(AllUsersName.class).toProvider(AllUsersNameProvider.class);
                bind(String.class).annotatedWith(GerritServerId.class).toInstance("gerrit");
                bind(GitRepositoryManager.class).toInstance(repoManager);
                bind(ProjectCache.class).toProvider(Providers.of(null));
                bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(testConfig);
                bind(String.class)
                    .annotatedWith(AnonymousCowardName.class)
                    .toProvider(AnonymousCowardNameProvider.class);
                bind(String.class)
                    .annotatedWith(CanonicalWebUrl.class)
                    .toInstance("http://localhost:8080/");
                bind(Boolean.class)
                    .annotatedWith(EnableReverseDnsLookup.class)
                    .toInstance(Boolean.TRUE);
                bind(Realm.class).to(FakeRealm.class);
                bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
                bind(AccountCache.class).toInstance(accountCache);
                bind(PersonIdent.class)
                    .annotatedWith(GerritPersonIdent.class)
                    .toInstance(serverIdent);
                bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
                bind(MetricMaker.class).to(DisabledMetricMaker.class);
                bind(ExecutorService.class)
                    .annotatedWith(FanOutExecutor.class)
                    .toInstance(assertableFanOutExecutor);
                bind(RobotClassifier.class).to(RobotClassifier.NoOp.class);
              }
            });

    injector.injectMembers(this);
    repoManager.createRepository(allUsers);
    changeOwner = userFactory.create(co.id());
    otherUser = userFactory.create(ou.id());
    otherUserId = otherUser.getAccountId();
    internalUser = new InternalUser();
  }

  private void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  protected Change newChange(boolean workInProgress) throws Exception {
    Change c = TestChanges.newChange(project, changeOwner.getAccountId());
    ChangeUpdate u = newUpdateForNewChange(c, changeOwner);
    u.setChangeId(c.getKey().get());
    u.setBranch(c.getDest().branch());
    u.setWorkInProgress(workInProgress);
    u.commit();
    return c;
  }

  protected Change newWorkInProgressChange() throws Exception {
    return newChange(true);
  }

  protected Change newChange() throws Exception {
    return newChange(false);
  }

  protected ChangeUpdate newUpdateForNewChange(Change c, CurrentUser user) throws Exception {
    return newUpdate(c, user, false);
  }

  protected ChangeUpdate newUpdate(Change c, CurrentUser user) throws Exception {
    return newUpdate(c, user, true);
  }

  protected ChangeUpdate newUpdate(Change c, CurrentUser user, boolean shouldExist)
      throws Exception {
    ChangeUpdate update = TestChanges.newUpdate(injector, c, user, shouldExist);
    update.setPatchSetId(c.currentPatchSetId());
    update.setAllowWriteToNewRef(true);
    return update;
  }

  protected ChangeNotes newNotes(Change c) {
    return new ChangeNotes(args, c, true, null).load();
  }

  protected static SubmitRecord submitRecord(
      String status, String errorMessage, SubmitRecord.Label... labels) {
    SubmitRecord rec = new SubmitRecord();
    rec.status = SubmitRecord.Status.valueOf(status);
    rec.errorMessage = errorMessage;
    if (labels.length > 0) {
      rec.labels = ImmutableList.copyOf(labels);
    }
    return rec;
  }

  protected static SubmitRecord.Label submitLabel(
      String name, String status, Account.Id appliedBy) {
    SubmitRecord.Label label = new SubmitRecord.Label();
    label.label = name;
    label.status = SubmitRecord.Label.Status.valueOf(status);
    label.appliedBy = appliedBy;
    return label;
  }

  protected HumanComment newComment(
      PatchSet.Id psId,
      String filename,
      String UUID,
      CommentRange range,
      int line,
      IdentifiedUser commenter,
      String parentUUID,
      Timestamp t,
      String message,
      short side,
      ObjectId commitId,
      boolean unresolved) {
    HumanComment c =
        new HumanComment(
            new Comment.Key(UUID, filename, psId.get()),
            commenter.getAccountId(),
            t,
            side,
            message,
            serverId,
            unresolved);
    c.lineNbr = line;
    c.parentUuid = parentUUID;
    c.setCommitId(commitId);
    c.setRange(range);
    return c;
  }

  protected static Timestamp truncate(Timestamp ts) {
    return new Timestamp((ts.getTime() / 1000) * 1000);
  }

  protected static Timestamp after(Change c, long millis) {
    return new Timestamp(c.getCreatedOn().getTime() + millis);
  }
}
