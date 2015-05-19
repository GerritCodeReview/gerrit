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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.GetRelated.ChangeAndCommit;
import com.google.gerrit.server.change.GetRelated.RelatedInfo;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
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
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    assertRelated(push.to("refs/for/master").getPatchSetId());
  }

  @Test
  public void getRelatedLinear() throws Exception {
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_2 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_2);
    pushHead(testRepo, "refs/for/master", false);

    for (RevCommit c : ImmutableList.of(c2_2, c1_1)) {
      assertRelated(getPatchSetId(c),
          changeAndCommit(id2, c2_2, 1, 1),
          changeAndCommit(id1, c1_1, 1, 1));
    }
  }

  @Test
  public void getRelatedReorder() throws Exception {
    // Create two commits and push.
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Swap the order of commits and push again.
    testRepo.reset("HEAD~2");
    RevCommit c2_2 = testRepo.cherryPick(c2_1);
    RevCommit c1_2 = testRepo.cherryPick(c1_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_1);
    PatchSet.Id ps2_2 = getPatchSetId(c2_1);

    for (PatchSet.Id ps : ImmutableList.of(ps2_2, ps1_2)) {
      assertRelated(ps,
          changeAndCommit(id1, c1_2, 2, 2),
          changeAndCommit(id2, c2_2, 2, 2));
    }

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(ps,
          changeAndCommit(id2, c2_1, 1, 2),
          changeAndCommit(id1, c1_1, 1, 2));
    }
  }

  @Test
  public void getRelatedReorderAndExtend() throws Exception {
    // Create two commits and push.
    ObjectId initial = repo().getRef("HEAD").getObjectId();
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);

    // Swap the order of commits, create a new commit on top, and push again.
    testRepo.reset(initial);
    RevCommit c2_2 = testRepo.cherryPick(c2_1);
    RevCommit c1_2 = testRepo.cherryPick(c1_1);
    RevCommit c3_1 = commitBuilder()
        .add("c.txt", "3")
        .message("subject: 3")
        .create();
    String id3 = getChangeId(c3_1);
    pushHead(testRepo, "refs/for/master", false);
    PatchSet.Id ps1_2 = getPatchSetId(c1_1);
    PatchSet.Id ps2_2 = getPatchSetId(c2_1);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    for (PatchSet.Id ps : ImmutableList.of(ps3_1, ps2_2, ps1_2)) {
      assertRelated(ps,
          changeAndCommit(id3, c3_1, 1, 1),
          changeAndCommit(id1, c1_2, 2, 2),
          changeAndCommit(id2, c2_2, 2, 2));
    }

    for (PatchSet.Id ps : ImmutableList.of(ps2_1, ps1_1)) {
      assertRelated(ps,
          changeAndCommit(id3, c3_1, 1, 1),
          changeAndCommit(id2, c2_1, 1, 2),
          changeAndCommit(id1, c1_1, 1, 2));
    }
  }

  @Test
  public void getRelatedEdit() throws Exception {
    RevCommit c1_1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(c1_1);
    RevCommit c2_1 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(c2_1);
    RevCommit c3_1 = commitBuilder()
        .add("c.txt", "3")
        .message("subject: 3")
        .create();
    String id3 = getChangeId(c3_1);
    pushHead(testRepo, "refs/for/master", false);

    Change ch2 = getChange(c2_1).change();
    editModifier.createEdit(ch2, getPatchSet(ch2));
    editModifier.modifyFile(editUtil.byChange(ch2).get(), "a.txt",
        RestSession.newRawInput(new byte[] {'a'}));
    ObjectId editRev =
        ObjectId.fromString(editUtil.byChange(ch2).get().getRevision().get());

    PatchSet.Id ps1_1 = getPatchSetId(c1_1);
    PatchSet.Id ps2_1 = getPatchSetId(c2_1);
    PatchSet.Id ps2_edit = new PatchSet.Id(ch2.getId(), 0);
    PatchSet.Id ps3_1 = getPatchSetId(c3_1);

    for (PatchSet.Id ps : ImmutableList.of(ps1_1, ps2_1, ps3_1)) {
      assertRelated(ps,
          changeAndCommit(id3, c3_1, 1, 1),
          changeAndCommit(id2, c2_1, 1, 1),
          changeAndCommit(id1, c1_1, 1, 1));
    }

    assertRelated(ps2_edit,
        changeAndCommit(id3, c3_1, 1, 1),
        changeAndCommit(id2, editRev, 0, 1),
        changeAndCommit(id1, c1_1, 1, 1));
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

  private String getChangeId(RevCommit c) throws Exception {
    return GitUtil.getChangeId(testRepo, c).get();
  }

  private PatchSet.Id getPatchSetId(ObjectId c) throws OrmException {
    return getChange(c).change().currentPatchSetId();
  }

  private PatchSet getPatchSet(Change c) throws OrmException {
    return db.patchSets().get(c.currentPatchSetId());
  }

  private ChangeData getChange(ObjectId c) throws OrmException {
    return Iterables.getOnlyElement(queryProvider.get().byCommit(c));
  }

  private static ChangeAndCommit changeAndCommit(String changeId,
      ObjectId commitId, int revisionNum, int currentRevisionNum) {
    ChangeAndCommit result = new ChangeAndCommit();
    result.changeId = changeId;
    result.commit = new CommitInfo();
    result.commit.commit = commitId.name();
    result._revisionNumber = revisionNum;
    result._currentRevisionNumber = currentRevisionNum;
    return result;
  }

  private void assertRelated(PatchSet.Id psId, ChangeAndCommit... expected)
      throws Exception {
    List<ChangeAndCommit> actual = getRelated(psId);
    assertThat(actual).hasSize(expected.length);
    for (int i = 0; i < actual.size(); i++) {
      String name = "index " + i + " related to " + psId;
      ChangeAndCommit a = actual.get(i);
      ChangeAndCommit e = expected[i];
      assertThat(a.changeId).named("Change-Id of " + name)
          .isEqualTo(e.changeId);
      assertThat(a.commit.commit).named("commit of " + name)
          .isEqualTo(e.commit.commit);
      // Don't bother checking _changeNumber; assume changeId is sufficient.
      assertThat(a._revisionNumber).named("revision of " + name)
          .isEqualTo(e._revisionNumber);
      assertThat(a._currentRevisionNumber).named("current revision of " + name)
          .isEqualTo(e._currentRevisionNumber);
    }
  }
}
