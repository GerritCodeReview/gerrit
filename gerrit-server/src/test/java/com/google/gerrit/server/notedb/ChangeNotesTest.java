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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.testutil.InMemoryRepositoryManager;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

public class ChangeNotesTest {
  private static final TimeZone TZ =
      TimeZone.getTimeZone("America/Los_Angeles");

  private Project.NameKey project;
  private InMemoryRepository repo;
  private volatile long clockStepMs;

  @Before
  public void setUp() {
    project = new Project.NameKey("test-project");
    repo = InMemoryRepositoryManager.newRepository(project);
  }

  @Before
  public void setMillisProvider() {
    clockStepMs = MILLISECONDS.convert(1, SECONDS);
    final AtomicLong clockMs = new AtomicLong(
        MILLISECONDS.convert(ChangeUtil.SORT_KEY_EPOCH_MINS, MINUTES)
        + MILLISECONDS.convert(60, DAYS));

    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @After
  public void resetMillisProvider() {
    DateTimeUtils.setCurrentMillisSystem();
  }

  @Test
  public void approvalsCommitFormat() throws Exception {
    Change c = newChange(5);
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) -1);
    update.putApproval("Verified", (short) 1);
    commit(update);
    assertEquals("refs/gerrit/changes/01/1", update.getRefName());

    RevWalk walk = new RevWalk(repo);
    try {
      RevCommit commit = walk.parseCommit(update.getRevision());
      walk.parseBody(commit);
      assertEquals("Update patch set 1\n"
          + "\n"
          + "Gerrit-Patch-Set: 1\n"
          + "Gerrit-Account: 5\n"
          + "Gerrit-Vote: code-review=-1\n"
          + "Gerrit-Vote: verified=+1\n",
          commit.getFullMessage());

      PersonIdent author = commit.getAuthorIdent();
      assertEquals("Example User", author.getName());
      assertEquals("user@example.com", author.getEmailAddress());
      assertEquals(new Date(c.getCreatedOn().getTime() + 1000),
          author.getWhen());
      assertEquals(TimeZone.getTimeZone("GMT-8:00"), author.getTimeZone());

      PersonIdent committer = commit.getCommitterIdent();
      assertEquals("Gerrit Test", committer.getName());
      assertEquals("test@gerrit.com", committer.getEmailAddress());
      assertEquals(author.getWhen(), committer.getWhen());
      assertEquals(author.getTimeZone(), committer.getTimeZone());
    } finally {
      walk.release();
    }
  }

  @Test
  public void approvalsOnePatchSet() throws Exception {
    Change c = newChange(5);
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) -1);
    update.putApproval("Verified", (short) 1);
    commit(update);

    ChangeNotes notes = new ChangeNotes(repo, c);
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(5, psas.get(0).getAccountId().get());
    assertEquals("code-review", psas.get(0).getLabel());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(after(c, 1000)), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(5, psas.get(1).getAccountId().get());
    assertEquals("verified", psas.get(1).getLabel());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(psas.get(0).getGranted(), psas.get(1).getGranted());
  }

  @Test
  public void approvalsMultiplePatchSets() throws Exception {
    Change c = newChange(5);
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) -1);
    commit(update);
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) 1);
    commit(update);
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = new ChangeNotes(repo, c);
    ListMultimap<PatchSet.Id, PatchSetApproval> psas = notes.getApprovals();
    assertEquals(2, notes.getApprovals().keySet().size());

    PatchSetApproval psa1 = Iterables.getOnlyElement(psas.get(ps1));
    assertEquals(ps1, psa1.getPatchSetId());
    assertEquals(5, psa1.getAccountId().get());
    assertEquals("code-review", psa1.getLabel());
    assertEquals((short) -1, psa1.getValue());
    assertEquals(truncate(after(c, 1000)), psa1.getGranted());

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertEquals(ps2, psa2.getPatchSetId());
    assertEquals(5, psa2.getAccountId().get());
    assertEquals("code-review", psa2.getLabel());
    assertEquals((short) +1, psa2.getValue());
    assertEquals(truncate(after(c, 2000)), psa2.getGranted());
  }

  @Test
  public void approvalsMultipleVotes() throws Exception {
    Change c = newChange(5);
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) -1);
    commit(update);

    ChangeNotes notes = new ChangeNotes(repo, c);
    PatchSetApproval psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertEquals("code-review", psa.getLabel());
    assertEquals((short) -1, psa.getValue());

    update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) 1);
    commit(update);

    notes = new ChangeNotes(repo, c);
    psa = Iterables.getOnlyElement(
        notes.getApprovals().get(c.currentPatchSetId()));
    assertEquals("code-review", psa.getLabel());
    assertEquals((short) 1, psa.getValue());
  }

  @Test
  public void approvalsMultipleUsers() throws Exception {
    Change c = newChange(5);
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), null);
    update.putApproval("Code-Review", (short) -1);
    commit(update);

    update = new ChangeUpdate(repo, c, new Account.Id(6), null);
    update.putApproval("Code-Review", (short) 1);
    commit(update);

    ChangeNotes notes = new ChangeNotes(repo, c);
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(5, psas.get(0).getAccountId().get());
    assertEquals("code-review", psas.get(0).getLabel());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(after(c, 1000)), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(6, psas.get(1).getAccountId().get());
    assertEquals("code-review", psas.get(1).getLabel());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(truncate(after(c, 2000)), psas.get(1).getGranted());
  }

  private Change newChange(int accountId) {
    Change.Id changeId = new Change.Id(1);
    Change c = new Change(
        new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        changeId,
        new Account.Id(accountId),
        new Branch.NameKey(project, "master"),
        TimeUtil.nowTs());
    incrementPatchSet(c);
    return c;
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

  private RevCommit commit(ChangeUpdate update) throws IOException {
    MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
        project, repo);
    Timestamp ts = TimeUtil.nowTs();
    md.getCommitBuilder().setAuthor(
        new PersonIdent("Example User", "user@example.com", ts, TZ));
    md.getCommitBuilder().setCommitter(
        new PersonIdent("Gerrit Test", "test@gerrit.com", ts, TZ));
    return update.commit(md);
  }
}
