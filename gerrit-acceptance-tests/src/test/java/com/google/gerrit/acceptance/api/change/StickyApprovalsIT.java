// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;
import static org.junit.Assert.fail;

import static com.google.gerrit.extensions.client.ChangeKind.NO_CODE_CHANGE;
import static com.google.gerrit.extensions.client.ChangeKind.REWORK;
import static com.google.gerrit.extensions.client.ChangeKind.TRIVIAL_REBASE;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

@NoHttpd
public class StickyApprovalsIT extends AbstractDaemonTest {
  private int count;

  @Before
  public void setup() throws Exception {
    count = 1;

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();

    // Overwrite "Code-Review" label that is inherited from All-Projects.
    // This way changes to the "Code Review" label don't affect other tests.
    LabelType codeReview =
        category("Code-Review", value(2, "Looks good to me, approved"),
            value(1, "Looks good to me, but someone else must approve"),
            value(0, "No score"),
            value(-1, "I would prefer that you didn't submit this"),
            value(-2, "Do not submit"));
    cfg.getLabelSections().put(codeReview.getName(), codeReview);

    LabelType verified = category("Verified", value(1, "Passes"),
        value(0, "No score"), value(-1, "Failed"));
    cfg.getLabelSections().put(verified.getName(), verified);

    AccountGroup.UUID all =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(cfg, Permission.forLabel(Util.codeReview().getName()), -2, 2,
        all, heads);
    Util.allow(cfg, Permission.forLabel(Util.verified().getName()), -1, 1, all,
        heads);
    saveProjectConfig(project, cfg);
  }

  @Test
  public void notSticky() throws Exception {
    assertNotSticky(EnumSet.of(REWORK, TRIVIAL_REBASE, NO_CODE_CHANGE));
  }

  @Test
  public void stickyOnVeto() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().get("Code-Review").setCopyMinScore(true);
    saveProjectConfig(project, cfg);

    for (ChangeKind changeKind : EnumSet.of(REWORK, TRIVIAL_REBASE,
        NO_CODE_CHANGE)) {
      testRepo.reset(getRemoteHead());

      String changeId = createChange().getChangeId();
      vote(admin, changeId, 2, 1);
      vote(user, changeId, -2, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, -2, 0, changeKind);
    }
  }

  @Test
  public void stickyOnTrivialRebase() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().get("Code-Review")
        .setCopyAllScoresOnTrivialRebase(true);
    saveProjectConfig(project, cfg);

    String changeId = createChange().getChangeId();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    updateChange(changeId, TRIVIAL_REBASE);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 2, 0, TRIVIAL_REBASE);
    assertVotes(c, user, -2, 0, TRIVIAL_REBASE);

    assertNotSticky(EnumSet.of(REWORK, NO_CODE_CHANGE));
  }

  @Test
  public void stickyOnNoCodeChange() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().get("Verified")
        .setCopyAllScoresIfNoCodeChange(true);
    saveProjectConfig(project, cfg);

    String changeId = createChange().getChangeId();
    vote(admin, changeId, 2, 1);
    vote(user, changeId, -2, -1);

    updateChange(changeId, NO_CODE_CHANGE);
    ChangeInfo c = detailedChange(changeId);
    assertVotes(c, admin, 0, 1, NO_CODE_CHANGE);
    assertVotes(c, user, 0, -1, NO_CODE_CHANGE);

    assertNotSticky(EnumSet.of(REWORK, TRIVIAL_REBASE));
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes().id(changeId)
        .get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
  }

  private void assertNotSticky(Set<ChangeKind> changeKinds) throws Exception {
    for (ChangeKind changeKind : changeKinds) {
      testRepo.reset(getRemoteHead());

      String changeId = createChange().getChangeId();
      vote(admin, changeId, +2, 1);
      vote(user, changeId, -2, -1);

      updateChange(changeId, changeKind);
      ChangeInfo c = detailedChange(changeId);
      assertVotes(c, admin, 0, 0, changeKind);
      assertVotes(c, user, 0, 0, changeKind);
    }
  }

  private void updateChange(String changeId, ChangeKind kind) throws Exception {
    switch (kind) {
      case NO_CODE_CHANGE:
        noCodeChange(changeId);
        return;
      case REWORK:
        rework(changeId);
        return;
      case TRIVIAL_REBASE:
        trivialRebase(changeId);
        return;
      case NO_CHANGE:
      case MERGE_FIRST_PARENT_UPDATE:
      default:
        fail("unexpexted change kind: " + kind);
    }
  }

  private void noCodeChange(String changeId) throws Exception {
    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo.amendRef("HEAD").insertChangeId(changeId.substring(1));
    commitBuilder.message("New subject " + count++).author(admin.getIdent())
        .committer(new PersonIdent(admin.getIdent(), testRepo.getDate()));
    commitBuilder.create();
    GitUtil.pushHead(testRepo, "refs/for/master", false);
    assertThat(getChangeKind(changeId)).isEqualTo(NO_CODE_CHANGE);
  }

  private void rework(String changeId) throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, PushOneCommit.FILE_NAME,
        "new content " + count++, changeId);
    push.to("refs/for/master").assertOkStatus();
    assertThat(getChangeKind(changeId)).isEqualTo(REWORK);
  }

  private void trivialRebase(String changeId) throws Exception {
    setApiUser(admin);
    testRepo.reset(getRemoteHead());
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        "Other Change", "a" + count++ + ".txt", PushOneCommit.FILE_CONTENT);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    RevisionApi revision = gApi.changes()
        .id(r.getChangeId())
        .current();
    ReviewInput in = new ReviewInput()
        .label("Code-Review", 2)
        .label("Verified", 1);
    revision.review(in);
    revision.submit();

    gApi.changes()
        .id(changeId)
        .current()
        .rebase();
    assertThat(getChangeKind(changeId)).isEqualTo(TRIVIAL_REBASE);
  }

  private ChangeKind getChangeKind(String changeId) throws Exception {
    ChangeInfo c = gApi.changes().id(changeId)
        .get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
    return c.revisions.get(c.currentRevision).kind;
  }

  private void vote(TestAccount user, String changeId, int codeReviewVote,
      int verifiedVote) throws Exception {
    setApiUser(user);
    ReviewInput in = new ReviewInput()
        .label("Code-Review", codeReviewVote)
        .label("Verified", verifiedVote);
    gApi.changes().id(changeId).current().review(in);
  }

  private void assertVotes(ChangeInfo c, TestAccount user, int codeReviewVote,
      int verifiedVote, ChangeKind changeKind) {
    assertVotes(c, user, "Code-Review", codeReviewVote, changeKind);
    assertVotes(c, user, "Verified", verifiedVote, changeKind);
  }

  private void assertVotes(ChangeInfo c, TestAccount user, String label,
      int expectedVote, ChangeKind changeKind) {
    Integer vote = null;
    for (ApprovalInfo approval : c.labels.get(label).all) {
      if (approval._accountId == user.id.get()) {
        vote = approval.value;
        break;
      }
    }
    assertThat(vote)
        .named("label = " + label + "; changeKind = " + changeKind.name())
        .isEqualTo(expectedVote);
  }
}
