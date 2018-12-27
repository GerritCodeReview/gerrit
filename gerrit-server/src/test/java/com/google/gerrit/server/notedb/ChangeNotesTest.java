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

package com.google.gerrit.server.notedb;

import static com.google.gerrit.server.notedb.ReviewerState.CC;
import static com.google.gerrit.server.notedb.ReviewerState.REVIEWER;
import static com.google.inject.Scopes.SINGLETON;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.FakeRealm;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;

import org.easymock.EasyMock;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

public class ChangeNotesTest {
  private static final TimeZone TZ =
      TimeZone.getTimeZone("America/Los_Angeles");

  private PersonIdent serverIdent;
  private Project.NameKey project;
  private InMemoryRepositoryManager repoManager;
  private InMemoryRepository repo;
  private FakeAccountCache accountCache;
  private IdentifiedUser changeOwner;
  private IdentifiedUser otherUser;
  private Injector injector;
  private String systemTimeZone;
  private volatile long clockStepMs;

  @Before
  public void setUp() throws Exception {
    setTimeForTesting();
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());

    serverIdent = new PersonIdent(
        "Gerrit Server", "noreply@gerrit.com", TimeUtil.nowTs(), TZ);
    project = new Project.NameKey("test-project");
    repoManager = new InMemoryRepositoryManager();
    repo = repoManager.createRepository(project);
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
        install(new GitModule());
        bind(NotesMigration.class).toInstance(NotesMigration.allEnabled());
        bind(GitRepositoryManager.class).toInstance(repoManager);
        bind(ProjectCache.class).toProvider(Providers.<ProjectCache> of(null));
        bind(CapabilityControl.Factory.class)
            .toProvider(Providers.<CapabilityControl.Factory> of(null));
        bind(Config.class).annotatedWith(GerritServerConfig.class)
            .toInstance(new Config());
        bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toProvider(AnonymousCowardNameProvider.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toInstance("http://localhost:8080/");
        bind(Realm.class).to(FakeRealm.class);
        bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
        bind(AccountCache.class).toInstance(accountCache);
        bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class)
            .toInstance(serverIdent);
        bind(GitReferenceUpdated.class)
            .toInstance(GitReferenceUpdated.DISABLED);
      }
    });

    IdentifiedUser.GenericFactory userFactory =
        injector.getInstance(IdentifiedUser.GenericFactory.class);
    changeOwner = userFactory.create(co.getId());
    otherUser = userFactory.create(ou.getId());
  }

  private void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    clockStepMs = MILLISECONDS.convert(1, SECONDS);
    final AtomicLong clockMs = new AtomicLong(
        new DateTime(2009, 9, 30, 17, 0, 0).getMillis());

    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @After
  public void resetTime() {
    DateTimeUtils.setCurrentMillisSystem();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void approvalsCommitFormatSimple() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.putApproval("Code-Review", (short) -1);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();
    assertEquals("refs/changes/01/1/meta", update.getRefName());

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Patch-set: 1\n"
          + "Reviewer: Change Owner <1@gerrit>\n"
          + "CC: Other Account <2@gerrit>\n"
          + "Label: Code-Review=-1\n"
          + "Label: Verified=+1\n",
          commit.getFullMessage());

      PersonIdent author = commit.getAuthorIdent();
      assertEquals("Change Owner", author.getName());
      assertEquals("1@gerrit", author.getEmailAddress());
      assertEquals(new Date(c.getCreatedOn().getTime() + 1000),
          author.getWhen());
      assertEquals(TimeZone.getTimeZone("GMT-7:00"), author.getTimeZone());

      PersonIdent committer = commit.getCommitterIdent();
      assertEquals("Gerrit Server", committer.getName());
      assertEquals("noreply@gerrit.com", committer.getEmailAddress());
      assertEquals(author.getWhen(), committer.getWhen());
      assertEquals(author.getTimeZone(), committer.getTimeZone());
    } finally {
      walk.close();
    }
  }

  @Test
  public void approvalTombstoneCommitFormat() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.removeApproval("Code-Review");
    update.commit();

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Patch-set: 1\n"
          + "Label: -Code-Review\n",
          commit.getFullMessage());
    } finally {
      walk.close();
    }
  }

  @Test
  public void submitCommitFormat() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.submit(ImmutableList.of(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Code-Review", "NEED", null)),
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Submit patch set 1\n"
          + "\n"
          + "Patch-set: 1\n"
          + "Status: submitted\n"
          + "Submitted-with: NOT_READY\n"
          + "Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
          + "Submitted-with: NEED: Code-Review\n"
          + "Submitted-with: NOT_READY\n"
          + "Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
          + "Submitted-with: NEED: Alternative-Code-Review\n",
          commit.getFullMessage());

      PersonIdent author = commit.getAuthorIdent();
      assertEquals("Change Owner", author.getName());
      assertEquals("1@gerrit", author.getEmailAddress());
      assertEquals(new Date(c.getCreatedOn().getTime() + 1000),
          author.getWhen());
      assertEquals(TimeZone.getTimeZone("GMT-7:00"), author.getTimeZone());

      PersonIdent committer = commit.getCommitterIdent();
      assertEquals("Gerrit Server", committer.getName());
      assertEquals("noreply@gerrit.com", committer.getEmailAddress());
      assertEquals(author.getWhen(), committer.getWhen());
      assertEquals(author.getTimeZone(), committer.getTimeZone());
    } finally {
      walk.close();
    }
  }

  @Test
  public void submitWithErrorMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.submit(ImmutableList.of(
        submitRecord("RULE_ERROR", "Problem with patch set:\n1")));
    update.commit();

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Submit patch set 1\n"
          + "\n"
          + "Patch-set: 1\n"
          + "Status: submitted\n"
          + "Submitted-with: RULE_ERROR Problem with patch set: 1\n",
          commit.getFullMessage());
    } finally {
      walk.close();
    }
  }

  @Test
  public void approvalsOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(1, psas.get(0).getAccountId().get());
    assertEquals("Code-Review", psas.get(0).getLabel());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(after(c, 1000)), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(1, psas.get(1).getAccountId().get());
    assertEquals("Verified", psas.get(1).getLabel());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(psas.get(0).getGranted(), psas.get(1).getGranted());
  }

  @Test
  public void approvalsMultiplePatchSets() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, PatchSetApproval> psas = notes.getApprovals();
    assertEquals(2, notes.getApprovals().keySet().size());

    PatchSetApproval psa1 = Iterables.getOnlyElement(psas.get(ps1));
    assertEquals(ps1, psa1.getPatchSetId());
    assertEquals(1, psa1.getAccountId().get());
    assertEquals("Code-Review", psa1.getLabel());
    assertEquals((short) -1, psa1.getValue());
    assertEquals(truncate(after(c, 1000)), psa1.getGranted());

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertEquals(ps2, psa2.getPatchSetId());
    assertEquals(1, psa2.getAccountId().get());
    assertEquals("Code-Review", psa2.getLabel());
    assertEquals((short) +1, psa2.getValue());
    assertEquals(truncate(after(c, 2000)), psa2.getGranted());
  }

  @Test
  public void approvalsMultipleApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertEquals("Code-Review", psa.getLabel());
    assertEquals((short) -1, psa.getValue());

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    notes = newNotes(c);
    psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertEquals("Code-Review", psa.getLabel());
    assertEquals((short) 1, psa.getValue());
  }

  @Test
  public void approvalsMultipleUsers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(1, psas.get(0).getAccountId().get());
    assertEquals("Code-Review", psas.get(0).getLabel());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(after(c, 1000)), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(2, psas.get(1).getAccountId().get());
    assertEquals("Code-Review", psas.get(1).getLabel());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(truncate(after(c, 2000)), psas.get(1).getGranted());
  }

  @Test
  public void approvalsTombstone() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Not-For-Long", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertEquals(1, psa.getAccountId().get());
    assertEquals("Not-For-Long", psa.getLabel());
    assertEquals((short) 1, psa.getValue());

    update = newUpdate(c, changeOwner);
    update.removeApproval("Not-For-Long");
    update.commit();

    notes = newNotes(c);
    assertTrue(notes.getApprovals().isEmpty());
  }

  @Test
  public void multipleReviewers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(1),
          REVIEWER, new Account.Id(2)),
        notes.getReviewers());
  }

  @Test
  public void reviewerTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(1),
          CC, new Account.Id(2)),
        notes.getReviewers());
  }

  @Test
  public void oneReviewerMultipleTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          REVIEWER, new Account.Id(2)),
        notes.getReviewers());

    update = newUpdate(c, otherUser);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    notes = newNotes(c);
    assertEquals(ImmutableSetMultimap.of(
          CC, new Account.Id(2)),
        notes.getReviewers());
  }

  @Test
  public void removeReviewer() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> psas =
        notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());
    assertEquals(changeOwner.getAccount().getId(), psas.get(0).getAccountId());
    assertEquals(otherUser.getAccount().getId(), psas.get(1).getAccountId());

    update = newUpdate(c, changeOwner);
    update.removeReviewer(otherUser.getAccount().getId());
    update.commit();

    notes = newNotes(c);
    psas = notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(1, psas.size());
    assertEquals(changeOwner.getAccount().getId(), psas.get(0).getAccountId());
  }

  @Test
  public void submitRecords() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.submit(ImmutableList.of(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Code-Review", "NEED", null)),
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<SubmitRecord> recs = notes.getSubmitRecords();
    assertEquals(2, recs.size());
    assertEquals(submitRecord("NOT_READY", null,
        submitLabel("Verified", "OK", changeOwner.getAccountId()),
        submitLabel("Code-Review", "NEED", null)), recs.get(0));
    assertEquals(submitRecord("NOT_READY", null,
        submitLabel("Verified", "OK", changeOwner.getAccountId()),
        submitLabel("Alternative-Code-Review", "NEED", null)), recs.get(1));
  }

  @Test
  public void latestSubmitRecordsOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");
    update.submit(ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", otherUser.getAccountId()))));
    update.commit();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 2");
    update.submit(ImmutableList.of(
        submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId()))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertEquals(submitRecord("OK", null,
          submitLabel("Code-Review", "OK", changeOwner.getAccountId())),
        Iterables.getOnlyElement(notes.getSubmitRecords()));
  }

  @Test
  public void multipleUpdatesInBatch() throws Exception {
    Change c = newChange();
    ChangeUpdate update1 = newUpdate(c, changeOwner);
    update1.putApproval("Verified", (short) 1);

    ChangeUpdate update2 = newUpdate(c, otherUser);
    update2.putApproval("Code-Review", (short) 2);

    BatchMetaDataUpdate batch = update1.openUpdate();
    try {
      batch.write(update1, new CommitBuilder());
      batch.write(update2, new CommitBuilder());
      batch.commit();
    } finally {
      batch.close();
    }

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> psas =
        notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(changeOwner.getAccount().getId(), psas.get(0).getAccountId());
    assertEquals("Verified", psas.get(0).getLabel());
    assertEquals((short) 1, psas.get(0).getValue());

    assertEquals(otherUser.getAccount().getId(), psas.get(1).getAccountId());
    assertEquals("Code-Review", psas.get(1).getLabel());
    assertEquals((short) 2, psas.get(1).getValue());
  }

  private Change newChange() {
    Change.Id changeId = new Change.Id(1);
    Change c = new Change(
        new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        changeId,
        changeOwner.getAccount().getId(),
        new Branch.NameKey(project, "master"),
        TimeUtil.nowTs());
    incrementPatchSet(c);
    return c;
  }

  private ChangeUpdate newUpdate(Change c, final IdentifiedUser user)
      throws Exception {
    return injector.createChildInjector(new FactoryModule() {
      @Override
      public void configure() {
        factory(ChangeUpdate.Factory.class);
        bind(IdentifiedUser.class).toInstance(user);
      }
    }).getInstance(ChangeUpdate.Factory.class).create(
        stubChangeControl(c, user), TimeUtil.nowTs(),
        Ordering.<String> natural());
  }

  private ChangeNotes newNotes(Change c) throws OrmException {
    return new ChangeNotes(repoManager, c).load();
  }

  private static void incrementPatchSet(Change change) {
    PatchSet.Id curr = change.currentPatchSetId();
    PatchSetInfo ps = new PatchSetInfo(new PatchSet.Id(
        change.getId(), curr != null ? curr.get() + 1 : 1));
    ps.setSubject("Change subject");
    change.setCurrentPatchSet(ps);
  }

  private static Timestamp truncate(Timestamp ts) {
    return new Timestamp((ts.getTime() / 1000) * 1000);
  }

  private static Timestamp after(Change c, long millis) {
    return new Timestamp(c.getCreatedOn().getTime() + millis);
  }

  private ChangeControl stubChangeControl(Change c, IdentifiedUser user) {
    ChangeControl ctl = EasyMock.createNiceMock(ChangeControl.class);
    expect(ctl.getChange()).andStubReturn(c);
    expect(ctl.getCurrentUser()).andStubReturn(user);
    EasyMock.replay(ctl);
    return ctl;
  }

  private static SubmitRecord submitRecord(String status,
      String errorMessage, SubmitRecord.Label... labels) {
    SubmitRecord rec = new SubmitRecord();
    rec.status = SubmitRecord.Status.valueOf(status);
    rec.errorMessage = errorMessage;
    if (labels.length > 0) {
      rec.labels = ImmutableList.copyOf(labels);
    }
    return rec;
  }

  private static SubmitRecord.Label submitLabel(String name, String status,
      Account.Id appliedBy) {
    SubmitRecord.Label label = new SubmitRecord.Label();
    label.label = name;
    label.status = SubmitRecord.Label.Status.valueOf(status);
    label.appliedBy = appliedBy;
    return label;
  }
}
