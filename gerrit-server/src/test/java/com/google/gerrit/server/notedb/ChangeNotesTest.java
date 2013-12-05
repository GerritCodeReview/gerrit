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
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.testutil.InMemoryRepositoryManager;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;

public class ChangeNotesTest {
  private Project.NameKey project;
  private InMemoryRepository repo;

  @Before
  public void setUp() {
    project = new Project.NameKey("test-project");
    repo = InMemoryRepositoryManager.newRepository(project);
  }

  @Test
  public void approvalsCommitFormat() throws Exception {
    Change c = newChange(1, 5);
    Timestamp ts = TimeUtil.nowTs();
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), ts);
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
    } finally {
      walk.release();
    }
  }

  @Test
  public void approvalsOnePatchSet() throws Exception {
    Change c = newChange(1, 5);
    Timestamp ts = TimeUtil.nowTs();
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), ts);
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
    assertEquals("verified", psas.get(0).getLabelId().get());
    assertEquals((short) 1, psas.get(0).getValue());
    assertEquals(truncate(ts), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(5, psas.get(1).getAccountId().get());
    assertEquals("code-review", psas.get(1).getLabelId().get());
    assertEquals((short) -1, psas.get(1).getValue());
    assertEquals(truncate(ts), psas.get(1).getGranted());
  }

  @Test
  public void approvalsMultiplePatchSets() throws Exception {
    Change c = newChange(1, 5);
    Timestamp ts1 = TimeUtil.nowTs();
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), ts1);
    update.putApproval("Code-Review", (short) -1);
    commit(update);
    PatchSet.Id ps1 = c.currentPatchSetId();

    c.setCurrentPatchSet(newPatchSet(c.getId(), 2));
    Timestamp ts2 = new Timestamp(ts1.getTime() + 2000);
    update = new ChangeUpdate(repo, c, c.getOwner(), ts2);
    update.putApproval("Code-Review", (short) 1);
    commit(update);
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = new ChangeNotes(repo, c);
    ListMultimap<PatchSet.Id, PatchSetApproval> psas = notes.getApprovals();
    assertEquals(2, notes.getApprovals().keySet().size());

    PatchSetApproval psa1 = Iterables.getOnlyElement(psas.get(ps1));
    assertEquals(ps1, psa1.getPatchSetId());
    assertEquals(5, psa1.getAccountId().get());
    assertEquals("code-review", psa1.getLabelId().get());
    assertEquals((short) -1, psa1.getValue());
    assertEquals(truncate(ts1), psa1.getGranted());

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertEquals(ps2, psa2.getPatchSetId());
    assertEquals(5, psa2.getAccountId().get());
    assertEquals("code-review", psa2.getLabelId().get());
    assertEquals((short) +1, psa2.getValue());
    assertEquals(truncate(ts2), psa2.getGranted());
  }

  @Test
  public void approvalsMultipleUsers() throws Exception {
    Change c = newChange(1, 5);
    Timestamp ts1 = TimeUtil.nowTs();
    ChangeUpdate update = new ChangeUpdate(repo, c, c.getOwner(), ts1);
    update.putApproval("Code-Review", (short) -1);
    commit(update);

    Timestamp ts2 = new Timestamp(ts1.getTime() + 2000);
    update = new ChangeUpdate(repo, c, new Account.Id(6), ts2);
    update.putApproval("Code-Review", (short) 1);
    commit(update);

    ChangeNotes notes = new ChangeNotes(repo, c);
    assertEquals(1, notes.getApprovals().keySet().size());
    List<PatchSetApproval> psas =
      notes.getApprovals().get(c.currentPatchSetId());
    assertEquals(2, psas.size());

    assertEquals(c.currentPatchSetId(), psas.get(0).getPatchSetId());
    assertEquals(5, psas.get(0).getAccountId().get());
    assertEquals("code-review", psas.get(0).getLabelId().get());
    assertEquals((short) -1, psas.get(0).getValue());
    assertEquals(truncate(ts1), psas.get(0).getGranted());

    assertEquals(c.currentPatchSetId(), psas.get(1).getPatchSetId());
    assertEquals(6, psas.get(1).getAccountId().get());
    assertEquals("code-review", psas.get(1).getLabelId().get());
    assertEquals((short) 1, psas.get(1).getValue());
    assertEquals(truncate(ts2), psas.get(1).getGranted());
  }

  private Change newChange(int psId, int accountId) {
    Change.Id changeId = new Change.Id(1);
    Change c = new Change(
        new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        changeId,
        new Account.Id(accountId),
        new Branch.NameKey(project, "master"),
        TimeUtil.nowTs());
    c.setCurrentPatchSet(newPatchSet(changeId, psId));
    return c;
  }

  private PatchSetInfo newPatchSet(Change.Id changeId, int psId) {
    PatchSetInfo ps = new PatchSetInfo(new PatchSet.Id(changeId, psId));
    ps.setSubject("Change subject");
    return ps;
  }

  private static Timestamp truncate(Timestamp ts) {
    return new Timestamp((ts.getTime() / 1000) * 1000);
  }

  private RevCommit commit(ChangeUpdate update) throws IOException {
    MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
        project, repo);
    md.getCommitBuilder().setAuthor(
        new PersonIdent("Example User", "user@example.com"));
    md.getCommitBuilder().setCommitter(
        new PersonIdent("Gerrit Test", "test@gerrit.com"));
    return update.commit(md);
  }
}
