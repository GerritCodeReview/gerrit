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
import static com.google.gerrit.testutil.TestChanges.incrementPatchSet;
import static com.google.inject.Scopes.SINGLETON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
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
import com.google.gerrit.server.notedb.CommentsInNotesUtil;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.FakeRealm;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
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
  public void changeMessageCommitFormatSimple() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Just a little code change.\n"
        + "How about a new line");
    update.commit();
    assertEquals("refs/changes/01/1/meta", update.getRefName());

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Just a little code change.\n"
          + "How about a new line\n"
          + "\n"
          + "Patch-set: 1\n",
          commit.getFullMessage());
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

  @Test
  public void changeMessageOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("Just a little code change.\n");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    ChangeMessage cm = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("Just a little code change.\n",
        cm.getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm.getAuthor());
    assertEquals(ps1, cm.getPatchSetId());
  }

  @Test
  public void changeMessagesMultiplePatchSets() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("This is the change message for the first PS.");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);

    update.setChangeMessage("This is the change message for the second PS.");
    update.commit();
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(2, changeMessages.keySet().size());

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("This is the change message for the first PS.",
        cm1.getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm1.getAuthor());

    ChangeMessage cm2 = Iterables.getOnlyElement(changeMessages.get(ps2));
    assertEquals(ps1, cm1.getPatchSetId());
    assertEquals("This is the change message for the second PS.",
        cm2.getMessage());
    assertEquals(changeOwner.getAccount().getId(), cm2.getAuthor());
    assertEquals(ps2, cm2.getPatchSetId());
  }

  @Test
  public void noChangeMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.commit();

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Patch-set: 1\n"
          + "Reviewer: Change Owner <1@gerrit>\n",
          commit.getFullMessage());
    } finally {
      walk.close();
    }

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(0, changeMessages.keySet().size());
  }

  @Test
  public void changeMessageWithTrailingDoubleNewline() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing trailing double newline\n"
        + "\n");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Testing trailing double newline\n"
          + "\n"
          + "\n"
          + "\n"
          + "Patch-set: 1\n",
          commit.getFullMessage());
    } finally {
      walk.close();
    }

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("Testing trailing double newline\n" + "\n", cm1.getMessage());
    assertEquals(changeOwner.getAccount().getId(), cm1.getAuthor());

  }

  @Test
  public void changeMessageWithMultipleParagraphs() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Testing paragraph 1\n"
          + "\n"
          + "Testing paragraph 2\n"
          + "\n"
          + "Testing paragraph 3\n"
          + "\n"
          + "Patch-set: 1\n",
          commit.getFullMessage());
    } finally {
      walk.close();
    }

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertEquals("Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3", cm1.getMessage());
    assertEquals(changeOwner.getAccount().getId(), cm1.getAuthor());
  }

  @Test
  public void changeMessageMultipleInOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("First change message.\n");
    update.commit();

    PatchSet.Id ps1 = c.currentPatchSetId();

    update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("Second change message.\n");
    update.commit();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages =
        notes.getChangeMessages();
    assertEquals(1, changeMessages.keySet().size());

    List<ChangeMessage> cm = changeMessages.get(ps1);
    assertEquals(2, cm.size());
    assertEquals("First change message.\n",
        cm.get(0).getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm.get(0).getAuthor());
    assertEquals(ps1, cm.get(0).getPatchSetId());
    assertEquals("Second change message.\n",
        cm.get(1).getMessage());
    assertEquals(changeOwner.getAccount().getId(),
        cm.get(1).getAuthor());
    assertEquals(ps1, cm.get(1).getPatchSetId());
  }

  @Test
  public void patchLineCommentNotesFormatSide1() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String message1 = "comment 1";
    String message2 = "comment 2";
    String message3 = "comment 3";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    Timestamp time3 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment1 = newPublishedPatchLineComment(psId, "file1",
        uuid, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, "file1",
        uuid, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range3 = new CommentRange(3, 1, 4, 1);
    PatchLineComment comment3 = newPublishedPatchLineComment(psId, "file2",
        uuid, range3, range3.getEndLine(), otherUser, null, time3, message3,
        (short) 1, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment3);
    update.commit();

    ChangeNotes notes = newNotes(c);

    RevWalk walk = new RevWalk(repo);
    ArrayList<Note> notesInTree =
        Lists.newArrayList(notes.getNoteMap().iterator());
    Note note = Iterables.getOnlyElement(notesInTree);

    byte[] bytes =
        walk.getObjectReader().open(
            note.getData(), Constants.OBJ_BLOB).getBytes();
    String noteString = new String(bytes, UTF_8);
    assertEquals("Patch-set: 1\n"
        + "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
        + "File: file1\n"
        + "\n"
        + "1:1-2:1\n"
        + CommentsInNotesUtil.formatTime(serverIdent, time1) + "\n"
        + "Author: Other Account <2@gerrit>\n"
        + "UUID: uuid\n"
        + "Bytes: 9\n"
        + "comment 1\n"
        + "\n"
        + "2:1-3:1\n"
        + CommentsInNotesUtil.formatTime(serverIdent, time2) + "\n"
        + "Author: Other Account <2@gerrit>\n"
        + "UUID: uuid\n"
        + "Bytes: 9\n"
        + "comment 2\n"
        + "\n"
        + "File: file2\n"
        + "\n"
        + "3:1-4:1\n"
        + CommentsInNotesUtil.formatTime(serverIdent, time3) + "\n"
        + "Author: Other Account <2@gerrit>\n"
        + "UUID: uuid\n"
        + "Bytes: 9\n"
        + "comment 3\n"
        + "\n",
        noteString);
  }

  @Test
  public void patchLineCommentNotesFormatSide0() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String message1 = "comment 1";
    String message2 = "comment 2";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment comment1 = newPublishedPatchLineComment(psId, "file1",
        uuid, range1, range1.getEndLine(), otherUser, null, time1, message1,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, "file1",
        uuid, range2, range2.getEndLine(), otherUser, null, time2, message2,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    RevWalk walk = new RevWalk(repo);
    ArrayList<Note> notesInTree =
        Lists.newArrayList(notes.getNoteMap().iterator());
    Note note = Iterables.getOnlyElement(notesInTree);

    byte[] bytes =
        walk.getObjectReader().open(
            note.getData(), Constants.OBJ_BLOB).getBytes();
    String noteString = new String(bytes, UTF_8);
    assertEquals("Base-for-patch-set: 1\n"
        + "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
        + "File: file1\n"
        + "\n"
        + "1:1-2:1\n"
        + CommentsInNotesUtil.formatTime(serverIdent, time1) + "\n"
        + "Author: Other Account <2@gerrit>\n"
        + "UUID: uuid\n"
        + "Bytes: 9\n"
        + "comment 1\n"
        + "\n"
        + "2:1-3:1\n"
        + CommentsInNotesUtil.formatTime(serverIdent, time2) + "\n"
        + "Author: Other Account <2@gerrit>\n"
        + "UUID: uuid\n"
        + "Bytes: 9\n"
        + "comment 2\n"
        + "\n",
        noteString);
  }


  @Test
  public void patchLineCommentMultipleOnePatchsetOneFileBothSides()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String messageForBase = "comment for base";
    String messageForPS = "comment for ps";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    PatchLineComment commentForBase =
        newPublishedPatchLineComment(psId, "filename", uuid,
        range, range.getEndLine(), otherUser, null, now, messageForBase,
        (short) 0, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(commentForBase);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment commentForPS =
        newPublishedPatchLineComment(psId, "filename", uuid,
        range, range.getEndLine(), otherUser, null, now, messageForPS,
        (short) 1, "abcd4567abcd4567abcd4567abcd4567abcd4567");
    update.setPatchSetId(psId);
    update.putComment(commentForPS);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPS =
        notes.getPatchSetComments();
    assertEquals(commentsForBase.size(), 1);
    assertEquals(commentsForPS.size(), 1);

    assertEquals(commentForBase,
        Iterables.getOnlyElement(commentsForBase.get(psId)));
    assertEquals(commentForPS,
        Iterables.getOnlyElement(commentsForPS.get(psId)));
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFile() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename = "filename";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp timeForComment1 = TimeUtil.nowTs();
    Timestamp timeForComment2 = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedPatchLineComment(psId, filename,
        uuid, range, range.getEndLine(), otherUser, null, timeForComment1,
        "comment 1", side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, filename,
        uuid, range, range.getEndLine(), otherUser, null, timeForComment2,
        "comment 2", side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPS =
        notes.getPatchSetComments();
    assertEquals(commentsForBase.size(), 0);
    assertEquals(commentsForPS.size(), 2);

    ImmutableList<PatchLineComment> commentsForThisPS =
        (ImmutableList<PatchLineComment>) commentsForPS.get(psId);
    assertEquals(commentsForThisPS.size(), 2);
    PatchLineComment commentFromNotes1 = commentsForThisPS.get(0);
    PatchLineComment commentFromNotes2 = commentsForThisPS.get(1);

    assertEquals(comment1, commentFromNotes1);
    assertEquals(comment2, commentFromNotes2);
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetMultipleFiles()
      throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename1 = "filename1";
    String filename2 = "filename2";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedPatchLineComment(psId, filename1,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 1",
        side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    PatchLineComment comment2 = newPublishedPatchLineComment(psId, filename2,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment 2",
        side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(psId);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Multimap<PatchSet.Id, PatchLineComment> commentsForBase =
        notes.getBaseComments();
    Multimap<PatchSet.Id, PatchLineComment> commentsForPS =
        notes.getPatchSetComments();
    assertEquals(commentsForBase.size(), 0);
    assertEquals(commentsForPS.size(), 2);

    ImmutableList<PatchLineComment> commentsForThisPS =
        (ImmutableList<PatchLineComment>) commentsForPS.get(psId);
    assertEquals(commentsForThisPS.size(), 2);
    PatchLineComment commentFromNotes1 = commentsForThisPS.get(0);
    PatchLineComment commentFromNotes2 = commentsForThisPS.get(1);

    assertEquals(comment1, commentFromNotes1);
    assertEquals(comment2, commentFromNotes2);
  }

  @Test
  public void patchLineCommentMultiplePatchsets() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    PatchLineComment comment1 = newPublishedPatchLineComment(ps1, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps1",
        side, "abcd1234abcd1234abcd1234abcd1234abcd1234");
    update.setPatchSetId(ps1);
    update.putComment(comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    PatchLineComment comment2 = newPublishedPatchLineComment(ps2, filename,
        uuid, range, range.getEndLine(), otherUser, null, now, "comment on ps2",
        side, "abcd4567abcd4567abcd4567abcd4567abcd4567");
    update.setPatchSetId(ps2);
    update.putComment(comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    LinkedListMultimap<PatchSet.Id, PatchLineComment> commentsForBase =
        LinkedListMultimap.create(notes.getBaseComments());
    LinkedListMultimap<PatchSet.Id, PatchLineComment> commentsForPS =
        LinkedListMultimap.create(notes.getPatchSetComments());
    assertEquals(commentsForBase.keys().size(), 0);
    assertEquals(commentsForPS.values().size(), 2);

    List<PatchLineComment> commentsForPS1 = commentsForPS.get(ps1);
    assertEquals(commentsForPS1.size(), 1);
    PatchLineComment commentFromPs1 = commentsForPS1.get(0);

    List<PatchLineComment> commentsForPS2 = commentsForPS.get(ps2);
    assertEquals(commentsForPS2.size(), 1);
    PatchLineComment commentFromPs2 = commentsForPS2.get(0);

    assertEquals(comment1, commentFromPs1);
    assertEquals(comment2, commentFromPs2);
  }

  private Change newChange() {
    return TestChanges.newChange(project, changeOwner);
  }

  private PatchLineComment newPublishedPatchLineComment(PatchSet.Id psId,
      String filename, String UUID, CommentRange range, int line,
      IdentifiedUser commenter, String parentUUID, Timestamp t,
      String message, short side, String commitSHA1) {
    return newPatchLineComment(psId, filename, UUID, range, line, commenter,
        parentUUID, t, message, side, commitSHA1, Status.PUBLISHED);
  }

  private PatchLineComment newPatchLineComment(PatchSet.Id psId,
      String filename, String UUID, CommentRange range, int line,
      IdentifiedUser commenter, String parentUUID, Timestamp t,
      String message, short side, String commitSHA1, Status status) {
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

  private ChangeUpdate newUpdate(Change c, IdentifiedUser user)
      throws Exception {
    return TestChanges.newUpdate(injector, repoManager, c, user);
  }

  private ChangeNotes newNotes(Change c) throws OrmException {
    return new ChangeNotes(repoManager, c).load();
  }

  private static Timestamp truncate(Timestamp ts) {
    return new Timestamp((ts.getTime() / 1000) * 1000);
  }

  private static Timestamp after(Change c, long millis) {
    return new Timestamp(c.getCreatedOn().getTime() + millis);
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
