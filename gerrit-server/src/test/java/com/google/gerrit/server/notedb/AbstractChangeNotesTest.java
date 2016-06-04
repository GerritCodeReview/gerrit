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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.GerritBaseTests;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.TestNotesMigration;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Providers;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import java.sql.Timestamp;
import java.util.TimeZone;

@Ignore
public abstract class AbstractChangeNotesTest extends GerritBaseTests {
  private static final TimeZone TZ =
      TimeZone.getTimeZone("America/Los_Angeles");

  private static final NotesMigration MIGRATION =
      new TestNotesMigration().setAllEnabled(true);

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

  @Inject
  protected IdentifiedUser.GenericFactory userFactory;

  @Inject
  protected NoteDbUpdateManager.Factory updateManagerFactory;

  @Inject
  protected AllUsersName allUsers;

  @Inject
  protected ChangeNoteUtil noteUtil;

  @Inject
  protected AbstractChangeNotes.Args args;

  private Injector injector;
  private String systemTimeZone;

  @Before
  public void setUp() throws Exception {
    setTimeForTesting();
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());

    serverIdent = new PersonIdent(
        "Gerrit Server", "noreply@gerrit.com", TimeUtil.nowTs(), TZ);
    project = new Project.NameKey("test-project");
    repoManager = new InMemoryRepositoryManager();
    repo = repoManager.createRepository(project);
    tr = new TestRepository<>(repo);
    rw = tr.getRevWalk();
    accountCache = new FakeAccountCache();
    Account co = new Account(new Account.Id(1), TimeUtil.nowTs());
    co.setFullName("Change Owner");
    co.setPreferredEmail("change@owner.com");
    accountCache.put(co);
    Account ou = new Account(new Account.Id(2), TimeUtil.nowTs());
    ou.setFullName("Other Account");
    ou.setPreferredEmail("other@account.com");
    accountCache.put(ou);

    injector = Guice.createInjector(new FactoryModule() {
      @Override
      public void configure() {
        Config cfg = new Config();
        install(new GitModule());
        install(NoteDbModule.forTest(cfg));
        bind(AllUsersName.class).toProvider(AllUsersNameProvider.class);
        bind(String.class).annotatedWith(GerritServerId.class)
            .toInstance("gerrit");
        bind(NotesMigration.class).toInstance(MIGRATION);
        bind(GitRepositoryManager.class).toInstance(repoManager);
        bind(ProjectCache.class).toProvider(Providers.<ProjectCache> of(null));
        bind(CapabilityControl.Factory.class)
            .toProvider(Providers.<CapabilityControl.Factory> of(null));
        bind(Config.class).annotatedWith(GerritServerConfig.class)
            .toInstance(cfg);
        bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toProvider(AnonymousCowardNameProvider.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toInstance("http://localhost:8080/");
        bind(Boolean.class).annotatedWith(DisableReverseDnsLookup.class)
            .toInstance(Boolean.FALSE);
        bind(Realm.class).to(FakeRealm.class);
        bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
        bind(AccountCache.class).toInstance(accountCache);
        bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class)
            .toInstance(serverIdent);
        bind(GitReferenceUpdated.class)
            .toInstance(GitReferenceUpdated.DISABLED);
        bind(MetricMaker.class).to(DisabledMetricMaker.class);
        bind(ReviewDb.class).toProvider(Providers.<ReviewDb> of(null));
      }
    });

    injector.injectMembers(this);
    repoManager.createRepository(allUsers);
    changeOwner = userFactory.create(co.getId());
    otherUser = userFactory.create(ou.getId());
    otherUserId = otherUser.getAccountId();
    internalUser = new InternalUser(null);
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

  protected Change newChange() throws Exception {
    Change c = TestChanges.newChange(project, changeOwner.getAccountId());
    ChangeUpdate u = newUpdate(c, changeOwner);
    u.setChangeId(c.getKey().get());
    u.setBranch(c.getDest().get());
    u.commit();
    return c;
  }

  protected ChangeUpdate newUpdate(Change c, CurrentUser user)
      throws Exception {
    ChangeUpdate update = TestChanges.newUpdate(injector, c, user);
    update.setPatchSetId(c.currentPatchSetId());
    update.setAllowWriteToNewRef(true);
    return update;
  }

  protected ChangeNotes newNotes(Change c) throws OrmException {
    return new ChangeNotes(args, c).load();
  }

  protected static SubmitRecord submitRecord(String status,
      String errorMessage, SubmitRecord.Label... labels) {
    SubmitRecord rec = new SubmitRecord();
    rec.status = SubmitRecord.Status.valueOf(status);
    rec.errorMessage = errorMessage;
    if (labels.length > 0) {
      rec.labels = ImmutableList.copyOf(labels);
    }
    return rec;
  }

  protected static SubmitRecord.Label submitLabel(String name, String status,
      Account.Id appliedBy) {
    SubmitRecord.Label label = new SubmitRecord.Label();
    label.label = name;
    label.status = SubmitRecord.Label.Status.valueOf(status);
    label.appliedBy = appliedBy;
    return label;
  }

  protected PatchLineComment newPublishedComment(PatchSet.Id psId,
      String filename, String UUID, CommentRange range, int line,
      IdentifiedUser commenter, String parentUUID, Timestamp t,
      String message, short side, String commitSHA1) {
    return newComment(psId, filename, UUID, range, line, commenter,
        parentUUID, t, message, side, commitSHA1,
        PatchLineComment.Status.PUBLISHED);
  }

  protected PatchLineComment newComment(PatchSet.Id psId,
      String filename, String UUID, CommentRange range, int line,
      IdentifiedUser commenter, String parentUUID, Timestamp t,
      String message, short side, String commitSHA1,
      PatchLineComment.Status status) {
    PatchLineComment comment = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(psId, filename), UUID),
        line, commenter.getAccountId(), parentUUID, t);
    comment.setSide(side);
    comment.setMessage(message);
    comment.setRange(range);
    comment.setRevId(new RevId(commitSHA1));
    comment.setStatus(status);
    return comment;
  }

  protected static Timestamp truncate(Timestamp ts) {
    return new Timestamp((ts.getTime() / 1000) * 1000);
  }

  protected static Timestamp after(Change c, long millis) {
    return new Timestamp(c.getCreatedOn().getTime() + millis);
  }
}
