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

package com.google.gerrit.acceptance.server.change;

import static com.google.gerrit.acceptance.GitUtil.add;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil.Commit;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.GetRelated.ChangeAndCommit;
import com.google.gerrit.server.change.GetRelated.RelatedInfo;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class GetRelatedIT extends AbstractDaemonTest {
  @Inject
  private ChangeEditUtil editUtil;

  @Inject
  private ChangeEditModifier editModifier;

  @Test
  public void getRelatedNoResult() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PatchSet.Id ps = push.to(git, "refs/for/master").getPatchSetId();
    List<ChangeAndCommit> related = getRelated(ps);
    assertEquals(0, related.size());
  }

  @Test
  public void getRelatedLinear() throws Exception {
    add(git, "a.txt", "1");
    Commit c1 = createCommit(git, admin.getIdent(), "subject: 1");
    add(git, "b.txt", "2");
    Commit c2 = createCommit(git, admin.getIdent(), "subject: 2");
    pushHead(git, "refs/for/master", false);

    for (Commit c : ImmutableList.of(c2, c1)) {
      List<ChangeAndCommit> related = getRelated(getPatchSetId(c));
      assertEquals(2, related.size());
      assertEquals("related to " + c.getChangeId(), c2.getChangeId(), related.get(0).changeId);
      assertEquals("related to " + c.getChangeId(), c1.getChangeId(), related.get(1).changeId);
    }
  }

  @Test
  public void getRelatedReorder() throws Exception {
    // Create two commits and push.
    add(git, "a.txt", "1");
    Commit c1 = createCommit(git, admin.getIdent(), "subject: 1");
    add(git, "b.txt", "2");
    Commit c2 = createCommit(git, admin.getIdent(), "subject: 2");
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps1 = getPatchSetId(c1);
    PatchSet.Id c2ps1 = getPatchSetId(c2);

    // Swap the order of commits and push again.
    git.reset().setMode(ResetType.HARD).setRef("HEAD^^").call();
    git.cherryPick().include(c2.getCommit()).include(c1.getCommit()).call();
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps2 = getPatchSetId(c1);
    PatchSet.Id c2ps2 = getPatchSetId(c2);

    for (PatchSet.Id ps : ImmutableList.of(c2ps2, c1ps2)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertEquals(2, related.size());
      assertEquals("related to " + ps, c1.getChangeId(), related.get(0).changeId);
      assertEquals("related to " + ps, c2.getChangeId(), related.get(1).changeId);
    }

    for (PatchSet.Id ps : ImmutableList.of(c2ps1, c1ps1)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertEquals(2, related.size());
      assertEquals("related to " + ps, c2.getChangeId(), related.get(0).changeId);
      assertEquals("related to " + ps, c1.getChangeId(), related.get(1).changeId);
    }
  }

  @Test
  public void getRelatedReorderAndExtend() throws Exception {
    // Create two commits and push.
    add(git, "a.txt", "1");
    Commit c1 = createCommit(git, admin.getIdent(), "subject: 1");
    add(git, "b.txt", "2");
    Commit c2 = createCommit(git, admin.getIdent(), "subject: 2");
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps1 = getPatchSetId(c1);
    PatchSet.Id c2ps1 = getPatchSetId(c2);

    // Swap the order of commits, create a new commit on top, and push again.
    git.reset().setMode(ResetType.HARD).setRef("HEAD^^").call();
    git.cherryPick().include(c2.getCommit()).include(c1.getCommit()).call();
    add(git, "c.txt", "3");
    Commit c3 = createCommit(git, admin.getIdent(), "subject: 3");
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps2 = getPatchSetId(c1);
    PatchSet.Id c2ps2 = getPatchSetId(c2);
    PatchSet.Id c3ps1 = getPatchSetId(c3);


    for (PatchSet.Id ps : ImmutableList.of(c3ps1, c2ps2, c1ps2)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertEquals(3, related.size());
      assertEquals("related to " + ps, c3.getChangeId(), related.get(0).changeId);
      assertEquals("related to " + ps, c1.getChangeId(), related.get(1).changeId);
      assertEquals("related to " + ps, c2.getChangeId(), related.get(2).changeId);
    }

    for (PatchSet.Id ps : ImmutableList.of(c2ps1, c1ps1)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertEquals(3, related.size());
      assertEquals("related to " + ps, c3.getChangeId(), related.get(0).changeId);
      assertEquals("related to " + ps, c2.getChangeId(), related.get(1).changeId);
      assertEquals("related to " + ps, c1.getChangeId(), related.get(2).changeId);
    }
  }

  @Test
  public void getRelatedEdit() throws Exception {
    add(git, "a.txt", "1");
    Commit c1 = createCommit(git, admin.getIdent(), "subject: 1");
    add(git, "b.txt", "2");
    Commit c2 = createCommit(git, admin.getIdent(), "subject: 2");
    add(git, "b.txt", "3");
    Commit c3 = createCommit(git, admin.getIdent(), "subject: 3");
    pushHead(git, "refs/for/master", false);

    Change ch2 = getChange(c2);
    editModifier.createEdit(ch2, getPatchSet(ch2));
    String editRev = editUtil.byChange(ch2).get().getRevision().get();

    List<ChangeAndCommit> related = getRelated(ch2.getId(), 0);
    assertEquals(3, related.size());
    assertEquals("related to " + c2.getChangeId(), c3.getChangeId(), related.get(0).changeId);
    assertEquals("related to " + c2.getChangeId(), c2.getChangeId(), related.get(1).changeId);
    assertEquals("has edit revision number", 0, related.get(1)._revisionNumber.intValue());
    assertEquals("has edit revision " + editRev, editRev, related.get(1).commit.commit);
    assertEquals("related to " + c2.getChangeId(), c1.getChangeId(), related.get(2).changeId);
  }

  private List<ChangeAndCommit> getRelated(PatchSet.Id ps) throws IOException {
    return getRelated(ps.getParentKey(), ps.get());
  }

  private List<ChangeAndCommit> getRelated(Change.Id changeId, int ps)
      throws IOException {
    String url = String.format("/changes/%d/revisions/%d/related",
        changeId.get(), ps);
    return newGson().fromJson(adminSession.get(url).getReader(),
        RelatedInfo.class).changes;
  }

  private PatchSet.Id getPatchSetId(Commit c) throws OrmException {
    return getChange(c).currentPatchSetId();
  }

  private PatchSet getPatchSet(Change c) throws OrmException {
    return db.patchSets().get(c.currentPatchSetId());
  }

  private Change getChange(Commit c) throws OrmException {
    return Iterables.getOnlyElement(
        db.changes().byKey(new Change.Key(c.getChangeId())));
  }
}
