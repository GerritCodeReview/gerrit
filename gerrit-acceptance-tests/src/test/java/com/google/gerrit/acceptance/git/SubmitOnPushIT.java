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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import java.io.IOException;

@NoHttpd
public class SubmitOnPushIT extends AbstractDaemonTest {
  @Inject
  private ApprovalsUtil approvalsUtil;

  @Inject
  private ChangeNotes.Factory changeNotesFactory;

  @Test
  public void submitOnPush() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushWithTag() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");
    PushOneCommit.Tag tag = new PushOneCommit.Tag("v1.0");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setTag(tag);
    PushOneCommit.Result r = push.to("refs/for/master%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
    assertTag(project, "refs/heads/master", tag);
  }

  @Test
  public void submitOnPushWithAnnotatedTag() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    PushOneCommit.AnnotatedTag tag =
        new PushOneCommit.AnnotatedTag("v1.0", "annotation", admin.getIdent());
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setTag(tag);
    PushOneCommit.Result r = push.to("refs/for/master%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
    assertTag(project, "refs/heads/master", tag);
  }

  @Test
  public void submitOnPushToRefsMetaConfig() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/meta/config");

    git().fetch().setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config")).call();
    testRepo.reset("refs/meta/config");

    PushOneCommit.Result r = pushTo("refs/for/refs/meta/config%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/meta/config");
  }

  @Test
  public void submitOnPushMergeConflict() throws Exception {
    ObjectId objectId = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/master", "one change", "a.txt", "some content");
    testRepo.reset(objectId);

    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    PushOneCommit.Result r =
        push("refs/for/master%submit", "other change", "a.txt", "other content");
    r.assertErrorStatus();
    r.assertChange(Change.Status.NEW, null);
    r.assertMessage("Change " + r.getChange().getId()
        + ": change could not be merged due to a path conflict.");
  }

  @Test
  public void submitOnPushSuccessfulMerge() throws Exception {
    String master = "refs/heads/master";
    ObjectId objectId = repo().exactRef("HEAD").getObjectId();
    push(master, "one change", "a.txt", "some content");
    testRepo.reset(objectId);

    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    PushOneCommit.Result r =
        push("refs/for/master%submit", "other change", "b.txt", "other content");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertMergeCommit(master, "other change");
  }

  @Test
  public void submitOnPushNewPatchSet() throws Exception {
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");

    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    r = push("refs/for/master%submit", PushOneCommit.SUBJECT, "a.txt",
        "other content", r.getChangeId());
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    Change c = Iterables.getOnlyElement(
        queryProvider.get().byKeyPrefix(r.getChangeId())).change();
    assertThat(db.patchSets().byChange(c.getId()).toList()).hasSize(2);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushNotAllowed_Error() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertErrorStatus("submit not allowed");
  }

  @Test
  public void submitOnPushNewPatchSetNotAllowed_Error() throws Exception {
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");

    r = push("refs/for/master%submit", PushOneCommit.SUBJECT, "a.txt",
        "other content", r.getChangeId());
    r.assertErrorStatus("submit not allowed");
  }

  @Test
  public void submitOnPushingDraft_Error() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master%draft,submit");
    r.assertErrorStatus("cannot submit draft");
  }

  @Test
  public void submitOnPushToNonExistingBranch_Error() throws Exception {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName + "%submit");
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  @Test
  public void mergeOnPushToBranch() throws Exception {
    grant(Permission.PUSH, project, "refs/heads/master");
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");
    r.assertOkStatus();

    git().push()
        .setRefSpecs(new RefSpec(r.getCommitId().name() + ":refs/heads/master"))
        .call();
    assertCommit(project, "refs/heads/master");
    assertThat(getSubmitter(r.getPatchSetId())).isNull();
    Change c = db.changes().get(r.getPatchSetId().getParentKey());
    assertThat(c.getStatus()).isEqualTo(Change.Status.MERGED);
  }

  @Test
  public void mergeOnPushToBranchWithNewPatchset() throws Exception {
    grant(Permission.PUSH, project, "refs/heads/master");
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());

    r = push.to("refs/heads/master");
    r.assertOkStatus();

    assertCommit(project, "refs/heads/master");
    assertThat(getSubmitter(r.getPatchSetId())).isNull();
    Change c = db.changes().get(r.getPatchSetId().getParentKey());
    assertThat(c.getStatus()).isEqualTo(Change.Status.MERGED);
  }

  private PatchSetApproval getSubmitter(PatchSet.Id patchSetId)
      throws OrmException {
    Change c = db.changes().get(patchSetId.getParentKey());
    ChangeNotes notes = changeNotesFactory.create(c).load();
    return approvalsUtil.getSubmitter(db, notes, patchSetId);
  }

  private void assertSubmitApproval(PatchSet.Id patchSetId) throws OrmException {
    PatchSetApproval a = getSubmitter(patchSetId);
    assertThat(a.isSubmit()).isTrue();
    assertThat(a.getValue()).isEqualTo((short) 1);
    assertThat(a.getAccountId()).isEqualTo(admin.id);
  }

  private void assertCommit(Project.NameKey project, String branch) throws IOException {
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      RevCommit c = rw.parseCommit(r.exactRef(branch).getObjectId());
      assertThat(c.getShortMessage()).isEqualTo(PushOneCommit.SUBJECT);
      assertThat(c.getAuthorIdent().getEmailAddress()).isEqualTo(admin.email);
      assertThat(c.getCommitterIdent().getEmailAddress()).isEqualTo(
          admin.email);
    }
  }

  private void assertMergeCommit(String branch, String subject) throws IOException {
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      RevCommit c = rw.parseCommit(r.exactRef(branch).getObjectId());
      assertThat(c.getParentCount()).isEqualTo(2);
      assertThat(c.getShortMessage()).isEqualTo("Merge \"" + subject + "\"");
      assertThat(c.getAuthorIdent().getEmailAddress()).isEqualTo(admin.email);
      assertThat(c.getCommitterIdent().getEmailAddress()).isEqualTo(
          serverIdent.get().getEmailAddress());
    }
  }

  private void assertTag(Project.NameKey project, String branch,
      PushOneCommit.Tag tag) throws IOException {
    try (Repository repo = repoManager.openRepository(project)) {
      Ref tagRef = repo.exactRef(tag.name);
      assertThat(tagRef).isNotNull();
      ObjectId taggedCommit = null;
      if (tag instanceof PushOneCommit.AnnotatedTag) {
        PushOneCommit.AnnotatedTag annotatedTag = (PushOneCommit.AnnotatedTag)tag;
        try (RevWalk rw = new RevWalk(repo)) {
          RevObject object = rw.parseAny(tagRef.getObjectId());
          assertThat(object).isInstanceOf(RevTag.class);
          RevTag tagObject = (RevTag) object;
          assertThat(tagObject.getFullMessage())
              .isEqualTo(annotatedTag.message);
          assertThat(tagObject.getTaggerIdent()).isEqualTo(annotatedTag.tagger);
          taggedCommit = tagObject.getObject();
        }
      } else {
        taggedCommit = tagRef.getObjectId();
      }
      ObjectId headCommit = repo.exactRef(branch).getObjectId();
      assertThat(taggedCommit).isNotNull();
      assertThat(taggedCommit).isEqualTo(headCommit);
    }
  }

  private PushOneCommit.Result push(String ref, String subject,
      String fileName, String content) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, subject, fileName, content);
    return push.to(ref);
  }

  private PushOneCommit.Result push(String ref, String subject,
      String fileName, String content, String changeId) throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo, subject,
        fileName, content, changeId);
    return push.to(ref);
  }
}
