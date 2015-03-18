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
import static com.google.gerrit.acceptance.GitUtil.getChangeId;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestSession;
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
    PatchSet.Id ps = push.to("refs/for/master").getPatchSetId();
    List<ChangeAndCommit> related = getRelated(ps);
    assertThat(related).isEmpty();
  }

  @Test
  public void getRelatedLinear() throws Exception {
    RevCommit c1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(testRepo, c1).get();
    RevCommit c2 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(testRepo, c2).get();
    pushHead(git, "refs/for/master", false);

    for (RevCommit c : ImmutableList.of(c2, c1)) {
      List<ChangeAndCommit> related = getRelated(getPatchSetId(c));
      String id = getChangeId(testRepo, c).get();
      assertThat(related).hasSize(2);
      assertThat(related.get(0).changeId)
          .named("related to " + id).isEqualTo(id2);
      assertThat(related.get(1).changeId)
          .named("related to " + id).isEqualTo(id1);
    }
  }

  @Test
  public void getRelatedReorder() throws Exception {
    // Create two commits and push.
    RevCommit c1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(testRepo, c1).get();
    RevCommit c2 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(testRepo, c2).get();
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps1 = getPatchSetId(c1);
    PatchSet.Id c2ps1 = getPatchSetId(c2);

    // Swap the order of commits and push again.
    testRepo.reset("HEAD~2");
    testRepo.cherryPick(c2);
    testRepo.cherryPick(c1);
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps2 = getPatchSetId(c1);
    PatchSet.Id c2ps2 = getPatchSetId(c2);

    for (PatchSet.Id ps : ImmutableList.of(c2ps2, c1ps2)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertThat(related).hasSize(2);
      assertThat(related.get(0).changeId).named("related to " + ps)
          .isEqualTo(id1);
      assertThat(related.get(1).changeId).named("related to " + ps)
          .isEqualTo(id2);
    }

    for (PatchSet.Id ps : ImmutableList.of(c2ps1, c1ps1)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertThat(related).hasSize(2);
      assertThat(related.get(0).changeId).named("related to " + ps)
          .isEqualTo(id2);
      assertThat(related.get(1).changeId).named("related to " + ps)
          .isEqualTo(id1);
    }
  }

  @Test
  public void getRelatedReorderAndExtend() throws Exception {
    // Create two commits and push.
    ObjectId initial = testRepo.getRepository().getRef("HEAD").getObjectId();
    RevCommit c1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(testRepo, c1).get();
    RevCommit c2 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(testRepo, c2).get();
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps1 = getPatchSetId(c1);
    PatchSet.Id c2ps1 = getPatchSetId(c2);

    // Swap the order of commits, create a new commit on top, and push again.
    testRepo.reset(initial);
    testRepo.cherryPick(c2);
    testRepo.cherryPick(c1);
    RevCommit c3 = commitBuilder()
        .add("c.txt", "3")
        .message("subject: 3")
        .create();
    String id3 = getChangeId(testRepo, c3).get();
    pushHead(git, "refs/for/master", false);
    PatchSet.Id c1ps2 = getPatchSetId(c1);
    PatchSet.Id c2ps2 = getPatchSetId(c2);
    PatchSet.Id c3ps1 = getPatchSetId(c3);


    for (PatchSet.Id ps : ImmutableList.of(c3ps1, c2ps2, c1ps2)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertThat(related).hasSize(3);
      assertThat(related.get(0).changeId).named("related to " + ps)
          .isEqualTo(id3);
      assertThat(related.get(1).changeId).named("related to " + ps)
          .isEqualTo(id1);
      assertThat(related.get(2).changeId).named("related to " + ps)
          .isEqualTo(id2);
    }

    for (PatchSet.Id ps : ImmutableList.of(c2ps1, c1ps1)) {
      List<ChangeAndCommit> related = getRelated(ps);
      assertThat(related).hasSize(3);
      assertThat(related.get(0).changeId).named("related to " + ps)
          .isEqualTo(id3);
      assertThat(related.get(1).changeId).named("related to " + ps)
          .isEqualTo(id2);
      assertThat(related.get(2).changeId).named("related to " + ps)
          .isEqualTo(id1);
    }
  }

  @Test
  public void getRelatedEdit() throws Exception {
    RevCommit c1 = commitBuilder()
        .add("a.txt", "1")
        .message("subject: 1")
        .create();
    String id1 = getChangeId(testRepo, c1).get();
    RevCommit c2 = commitBuilder()
        .add("b.txt", "2")
        .message("subject: 2")
        .create();
    String id2 = getChangeId(testRepo, c2).get();
    RevCommit c3 = commitBuilder()
        .add("c.txt", "3")
        .message("subject: 3")
        .create();
    String id3 = getChangeId(testRepo, c3).get();
    pushHead(git, "refs/for/master", false);

    Change ch2 = getChange(c2).change();
    editModifier.createEdit(ch2, getPatchSet(ch2));
    editModifier.modifyFile(editUtil.byChange(ch2).get(), "a.txt",
        RestSession.newRawInput(new byte[] {'a'}));
    String editRev = editUtil.byChange(ch2).get().getRevision().get();

    List<ChangeAndCommit> related = getRelated(ch2.getId(), 0);
    assertThat(related).hasSize(3);
    assertThat(related.get(0).changeId).named("related to " + id2)
        .isEqualTo(id3);
    assertThat(related.get(1).changeId).named("related to " + id2)
        .isEqualTo(id2);
    assertThat(related.get(1)._revisionNumber).named(
        "has edit revision number").isEqualTo(0);
    assertThat(related.get(1).commit.commit).named(
        "has edit revision " + editRev).isEqualTo(editRev);
    assertThat(related.get(2).changeId).named("related to " + id2)
        .isEqualTo(id1);
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

  private PatchSet.Id getPatchSetId(ObjectId c) throws OrmException {
    return getChange(c).change().currentPatchSetId();
  }

  private PatchSet getPatchSet(Change c) throws OrmException {
    return db.patchSets().get(c.currentPatchSetId());
  }

  private ChangeData getChange(ObjectId c) throws OrmException {
    return Iterables.getOnlyElement(queryProvider.get().byCommit(c));
  }
}
