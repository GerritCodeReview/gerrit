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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class LabelTypeIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("notedb", null, "write", true);
    cfg.setBoolean("notedb", "patchSetApprovals", "read", true);
    return cfg;
  }

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  private LabelType codeReview;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    codeReview = checkNotNull(cfg.getLabelSections().get("Code-Review"));
    codeReview.setCopyMinScore(false);
    codeReview.setCopyMaxScore(false);
    codeReview.setCopyAllScoresOnTrivialRebase(false);
    codeReview.setCopyAllScoresIfNoCodeChange(false);
    saveProjectConfig(cfg);
  }

  @Test
  public void noCopyMinScoreOnRework() throws Exception {
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
    //assertApproval(r, -2);
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
  public void noCopyAllScoresIfNoCodeChange() throws Exception {
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, admin.getIdent(),
        "first subject", file, contents);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, admin.getIdent(),
        "second subject", file, contents, r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 0);
  }

  @Test
  public void copyAllScoresIfNoCodeChange() throws Exception {
    String file = "a.txt";
    String contents = "contents";
    codeReview.setCopyAllScoresIfNoCodeChange(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(),
        "first subject", file, contents);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, admin.getIdent(),
        "second subject", file, contents, r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 1);
  }

  @Test
  public void noCopyAllScoresOnTrivialRebase() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r1 = push.to(git, "refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent(),
        "non-conflicting", "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to(git, "refs/for/master");
    merge(r2);

    git.checkout().setName(r1.getCommit().name()).call();
    push = pushFactory.create(db, admin.getIdent(), subject, file, contents);
    PushOneCommit.Result r3 = push.to(git, "refs/for/master");
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

    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    PushOneCommit.Result r1 = push.to(git, "refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent(),
        "non-conflicting", "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to(git, "refs/for/master");
    merge(r2);

    git.checkout().setName(r1.getCommit().name()).call();
    push = pushFactory.create(db, admin.getIdent(), subject, file, contents);
    PushOneCommit.Result r3 = push.to(git, "refs/for/master");
    revision(r3).review(ReviewInput.recommend());
    assertApproval(r3, 1);

    rebase(r3);
    assertApproval(r3, 1);
  }

  private void saveLabelConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    cfg.getLabelSections().clear();
    cfg.getLabelSections().put(codeReview.getName(), codeReview);
    saveProjectConfig(cfg);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
  }

  private void merge(PushOneCommit.Result r) throws Exception {
    revision(r).review(ReviewInput.approve());
    revision(r).submit();
    Repository repo = repoManager.openRepository(project);
    try {
      assertEquals(r.getCommitId(),
          repo.getRef("refs/heads/master").getObjectId());
    } finally {
      repo.close();
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
    LabelInfo cr = c.labels.get("Code-Review");
    assertEquals(1, cr.all.size());
    assertEquals("Administrator", cr.all.get(0).name);
    assertEquals(expected, cr.all.get(0).value.intValue());
  }
}
