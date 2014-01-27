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
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.common.changes.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.grant;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.ChangeJson.LabelInfo;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class LabelTypeIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private GerritApi gApi;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private Project.NameKey project;
  private LabelType codeReview;
  private TestAccount user;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    user = accounts.user();
    project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    try {
      createProject(sshSession, project.get());
      git = cloneProject(sshSession.getUrl() + "/" + project.get());
      db = reviewDbProvider.open();
      atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
          identifiedUserFactory.create(Providers.of(db), user.getId())));
    } finally {
      sshSession.close();
    }

    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    grant(cfg, Permission.forLabel("Code-Review"), -2, 2, anonymousUsers,
        "refs/heads/*");
    grant(cfg, Permission.SUBMIT, anonymousUsers, "refs/heads/*");
    codeReview = checkNotNull(cfg.getLabelSections().get("Code-Review"));
    codeReview.setCopyMinScore(false);
    codeReview.setCopyMaxScore(false);
    codeReview.setCopyAllScoresOnTrivialRebase(false);
    codeReview.setCopyAllScoresIfNoCodeChange(false);
    saveProjectConfig(cfg);
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void noCopyMinScoreOnRework() throws Exception {
    String subject = "test commit";
    String file = "a.txt";

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        subject, file, "first contents");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.reject());
    assertApproval(r, -2);

    push = pushFactory.create(db, user.getIdent(),
        subject, file, "second contents", r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 0);
  }

  @Test
  public void copyMinScoreOnRework() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    codeReview.setCopyMinScore(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        subject, file, "first contents");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.reject());
    assertApproval(r, -2);

    push = pushFactory.create(db, user.getIdent(),
        subject, file, "second contents", r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, -2);
  }

  @Test
  public void noCopyMaxScoreOnRework() throws Exception {
    String subject = "test commit";
    String file = "a.txt";

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        subject, file, "first contents");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.approve());
    assertApproval(r, 2);

    push = pushFactory.create(db, user.getIdent(),
        subject, file, "second contents", r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 0);
  }

  @Test
  public void copyMaxScoreOnRework() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    codeReview.setCopyMaxScore(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        subject, file, "first contents");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.approve());
    assertApproval(r, 2);

    push = pushFactory.create(db, user.getIdent(),
        subject, file, "second contents", r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 2);
  }

  @Test
  public void noCopyNonMaxScoreOnRework() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    codeReview.setCopyMinScore(true);
    codeReview.setCopyMaxScore(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        subject, file, "first contents");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, user.getIdent(),
        subject, file, "second contents", r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 0);
  }

  @Test
  public void noCopyNonMinScoreOnRework() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    codeReview.setCopyMinScore(true);
    codeReview.setCopyMaxScore(true);
    saveLabelConfig();

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        subject, file, "first contents");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.dislike());
    assertApproval(r, -1);

    push = pushFactory.create(db, user.getIdent(),
        subject, file, "second contents", r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 0);
  }

  @Test
  public void noCopyAllScoresIfNoCodeChange() throws Exception {
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        "first subject", file, contents);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, user.getIdent(),
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

    PushOneCommit push = pushFactory.create(db, user.getIdent(),
        "first subject", file, contents);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    revision(r).review(ReviewInput.recommend());
    assertApproval(r, 1);

    push = pushFactory.create(db, user.getIdent(),
        "second subject", file, contents, r.getChangeId());
    r = push.to(git, "refs/for/master");
    assertApproval(r, 1);
  }

  @Test
  public void noCopyAllScoresOnTrivialRebase() throws Exception {
    String subject = "test commit";
    String file = "a.txt";
    String contents = "contents";

    PushOneCommit push = pushFactory.create(db, user.getIdent());
    PushOneCommit.Result r1 = push.to(git, "refs/for/master");
    merge(r1);

    push = pushFactory.create(db, user.getIdent(),
        "non-conflicting", "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to(git, "refs/for/master");
    merge(r2);

    git.checkout().setName(r1.getCommit().name()).call();
    push = pushFactory.create(db, user.getIdent(), subject, file, contents);
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

    PushOneCommit push = pushFactory.create(db, user.getIdent());
    PushOneCommit.Result r1 = push.to(git, "refs/for/master");
    merge(r1);

    push = pushFactory.create(db, user.getIdent(),
        "non-conflicting", "b.txt", "other contents");
    PushOneCommit.Result r2 = push.to(git, "refs/for/master");
    merge(r2);

    git.checkout().setName(r1.getCommit().name()).call();
    push = pushFactory.create(db, user.getIdent(), subject, file, contents);
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

  private RevisionApi revision(PushOneCommit.Result r) throws Exception {
    return gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name());
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
    LabelInfo cr = getChange(r).labels.get("Code-Review");
    assertEquals(1, cr.all.size());
    assertEquals("User", cr.all.get(0).name);
    assertEquals(expected, cr.all.get(0).value.intValue());
  }

  private ChangeInfo getChange(PushOneCommit.Result pr) throws IOException {
    return getChange(pr.getChangeId(), DETAILED_LABELS);
  }
}
