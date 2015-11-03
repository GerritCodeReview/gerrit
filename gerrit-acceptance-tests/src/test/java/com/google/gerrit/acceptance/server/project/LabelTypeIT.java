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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;

import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class LabelTypeIT extends AbstractDaemonTest {
  private LabelType codeReview;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    codeReview = Util.codeReview();
    codeReview.setDefaultValue((short)-1);
    cfg.getLabelSections().put(codeReview.getName(), codeReview);
    saveProjectConfig(cfg);
  }

  @Test
  public void failChangedLabelValueOnClosedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    merge(r);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is closed");
    revision(r).review(ReviewInput.reject());
  }

  @Test
  public void noCopyMinScoreOnRework() throws Exception {
    codeReview.setCopyMinScore(false);
    saveLabelConfig();

    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.reject());
    assertApproval(r, -2);
    r = amendChange(r.getChangeId());
    assertApproval(r, 0);
  }

  @Test
  public void copyMinScoreOnRework() throws Exception {
    codeReview.setCopyMinScore(true);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.reject());
    assertApproval(r, -2);
    r = amendChange(r.getChangeId());
    assertApproval(r, -2);
  }

  @Test
  public void noCopyMaxScoreOnRework() throws Exception {
    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.approve());
    assertApproval(r, 2);
    r = amendChange(r.getChangeId());
    assertApproval(r, 0);
  }

  @Test
  public void copyMaxScoreOnRework() throws Exception {
    codeReview.setCopyMaxScore(true);
    saveLabelConfig();
    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.approve());
    assertApproval(r, 2);
    r = amendChange(r.getChangeId());
    assertApproval(r, 2);
  }

  @Test
  public void noCopyNonMaxScoreOnRework() throws Exception {
    codeReview.setCopyMinScore(true);
    codeReview.setCopyMaxScore(true);
    saveLabelConfig();

    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);
    r = amendChange(r.getChangeId());
    assertApproval(r, 0);
  }

  @Test
  public void noCopyNonMinScoreOnRework() throws Exception {
    codeReview.setCopyMinScore(true);
    codeReview.setCopyMaxScore(true);
    saveLabelConfig();

    PushOneCommit.Result r = createChange();
    revision(r).review(ReviewInput.dislike());
    assertApproval(r, -1);
    r = amendChange(r.getChangeId());
    assertApproval(r, 0);
  }

  @Test
  public void noCopyAllScoresIfNoChange() throws Exception {
    codeReview.setCopyAllScoresIfNoChange(false);
    saveLabelConfig();
    PushOneCommit.Result patchSet = readyPatchSetForNoChangeRebase();
    rebase(patchSet);
    assertApproval(patchSet, 0);
  }

  @Test
  public void copyAllScoresIfNoChange() throws Exception {
    PushOneCommit.Result patchSet = readyPatchSetForNoChangeRebase();
    rebase(patchSet);
    assertApproval(patchSet, 1);
  }

  @Test
  public void noCopyAllScoresIfNoCodeChange() throws Exception {
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        "first subject", file, contents);
    PushOneCommit.Result r = push.to("refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        "second subject", file, contents, r.getChangeId());
    r = push.to("refs/for/master");
    assertApproval(r, 0);
  }

  @Test
  public void copyAllScoresIfNoCodeChange() throws Exception {
    String file = "a.txt";
    String contents = "contents";
    codeReview.setCopyAllScoresIfNoCodeChange(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        "first subject", file, contents);
    PushOneCommit.Result r = push.to("refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        "second subject", file, contents, r.getChangeId());
    r = push.to("refs/for/master");
    assertApproval(r, 1);
  }

  @Test
  public void noCopyAllScoresOnTrivialRebase() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        "non-conflicting", "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to("refs/for/master");
    merge(r2);

    testRepo.reset(r1.getCommit());
    push = pushFactory.create(db, admin.getIdent(), testRepo, subject, file, contents);
    PushOneCommit.Result r3 = push.to("refs/for/master");
    revision(r3).review(ReviewInput.recommend());
    assertApproval(r3, 1);

    rebase(r3);
    assertApproval(r3, 0);
  }

  @Test
  public void copyAllScoresOnTrivialRebase() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    String contents = "contents";
    codeReview.setCopyAllScoresOnTrivialRebase(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        "non-conflicting", "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to("refs/for/master");
    merge(r2);

    testRepo.reset(r1.getCommit());
    push = pushFactory.create(db, admin.getIdent(), testRepo, subject, file, contents);
    PushOneCommit.Result r3 = push.to("refs/for/master");
    revision(r3).review(ReviewInput.recommend());
    assertApproval(r3, 1);

    rebase(r3);
    assertApproval(r3, 1);
  }

  @Test
  public void copyAllScoresOnTrivialRebaseAndCherryPick() throws Exception {
    codeReview.setCopyAllScoresOnTrivialRebase(true);
    saveLabelConfig();

    PushOneCommit.Result r1 = createChange();
    testRepo.reset(r1.getCommit());

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to("refs/for/master");

    revision(r2).review(ReviewInput.recommend());

    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = String.format("%s\n\nChange-Id: %s",
        PushOneCommit.SUBJECT,
        r2.getChangeId());

    doAssertApproval(1,
        gApi.changes()
            .id(r2.getChangeId())
            .revision(r2.getCommit().name())
            .cherryPick(in)
            .get());
  }

  @Test
  public void copyNoScoresOnReworkAndCherryPick()
      throws Exception {
    codeReview.setCopyAllScoresOnTrivialRebase(true);
    saveLabelConfig();

    PushOneCommit.Result r1 = createChange();

    testRepo.reset(r1.getCommit());

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to("refs/for/master");

    revision(r2).review(ReviewInput.recommend());

    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = String.format("Cherry pick\n\nChange-Id: %s",
        r2.getChangeId());

    doAssertApproval(0,
        gApi.changes()
            .id(r2.getChangeId())
            .revision(r2.getCommit().name())
            .cherryPick(in)
            .get());
  }

  private PushOneCommit.Result readyPatchSetForNoChangeRebase()
      throws Exception {
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, file, contents);
    PushOneCommit.Result base = push.to("refs/for/master");
    merge(base);

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, file, contents + "M");
    PushOneCommit.Result basePlusM = push.to("refs/for/master");
    merge(basePlusM);

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, file, contents);
    PushOneCommit.Result basePlusMMinusM = push.to("refs/for/master");
    merge(basePlusMMinusM);

    testRepo.reset(base.getCommit());
    push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, file, contents + "MM");
    PushOneCommit.Result patchSet = push.to("refs/for/master");
    revision(patchSet).review(ReviewInput.recommend());
    return patchSet;
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().clear();
    cfg.getLabelSections().put(codeReview.getName(), codeReview);
    saveProjectConfig(cfg);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
  }

  @Override
  protected void merge(PushOneCommit.Result r) throws Exception {
    super.merge(r);
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRef("refs/heads/master").getObjectId()).isEqualTo(
          r.getCommitId());
    }
  }

  private void rebase(PushOneCommit.Result r) throws Exception {
    revision(r).rebase();
  }

  private void assertApproval(PushOneCommit.Result r, int expected)
      throws Exception {
    // Don't use asserts from PushOneCommit so we can test the round-trip
    // through JSON instead of querying the DB directly.
    ChangeInfo c = get(r.getChangeId());
    doAssertApproval(expected, c);
  }

  private void doAssertApproval(int expected, ChangeInfo c) {
    LabelInfo cr = c.labels.get("Code-Review");
    assertThat((int) cr.defaultValue).isEqualTo(-1);
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value).isEqualTo(expected);
  }
}
