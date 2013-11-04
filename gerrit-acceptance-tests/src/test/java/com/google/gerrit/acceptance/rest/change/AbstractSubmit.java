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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.google.common.collect.Maps;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.GitUtil;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class AbstractSubmit extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GitRepositoryManager repoManager;

  protected RestSession session;

  private TestAccount admin;
  private Project.NameKey project;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    session = new RestSession(server, admin);
    initSsh(admin);

    project = new Project.NameKey("p");

    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  protected abstract SubmitType getSubmitType();

  @Test
  public void submitToEmptyRepo() throws JSchException, IOException,
      GitAPIException {
    Git git = createProject(false);
    PushOneCommit.Result change = createChange(git);
    submit(change.getChangeId());
    assertEquals(change.getCommitId(), getRemoteHead().getId());
  }

  protected Git createProject() throws JSchException, IOException,
      GitAPIException {
    return createProject(true);
  }

  private Git createProject(boolean emptyCommit)
      throws JSchException, IOException, GitAPIException {
    SshSession sshSession = new SshSession(server, admin);
    try {
      GitUtil.createProject(sshSession, project.get(), null, emptyCommit);
      setSubmitType(getSubmitType());
      return cloneProject(sshSession.getUrl() + "/" + project.get());
    } finally {
      sshSession.close();
    }
  }

  private void setSubmitType(SubmitType submitType) throws IOException {
    ProjectConfigInput in = new ProjectConfigInput();
    in.submit_type = submitType;
    in.use_content_merge = InheritableBoolean.FALSE;
    RestResponse r = session.put("/projects/" + project.get() + "/config", in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
  }

  protected void setUseContentMerge() throws IOException {
    ProjectConfigInput in = new ProjectConfigInput();
    in.use_content_merge = InheritableBoolean.TRUE;
    RestResponse r = session.put("/projects/" + project.get() + "/config", in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
  }

  protected PushOneCommit.Result createChange(Git git) throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }

  protected PushOneCommit.Result createChange(Git git, String subject,
      String fileName, String content) throws GitAPIException, IOException {
    PushOneCommit push =
        new PushOneCommit(db, admin.getIdent(), subject, fileName, content);
    return push.to(git, "refs/for/master");
  }

  protected void submit(String changeId) throws IOException {
    submit(changeId, HttpStatus.SC_OK);
  }

  protected void submitWithConflict(String changeId) throws IOException {
    submit(changeId, HttpStatus.SC_CONFLICT);
  }

  private void submit(String changeId, int expectedStatus) throws IOException {
    approve(changeId);
    RestResponse r =
        session.post("/changes/" + changeId + "/submit",
            SubmitInput.waitForMerge());
    assertEquals(expectedStatus, r.getStatusCode());
    if (expectedStatus == HttpStatus.SC_OK) {
      ChangeInfo change =
          (new Gson()).fromJson(r.getReader(),
              new TypeToken<ChangeInfo>() {}.getType());
      assertEquals(Change.Status.MERGED, change.status);
    }
    r.consume();
  }

  private void approve(String changeId) throws IOException {
    ReviewInput in = new ReviewInput();
    in.labels = Maps.newHashMap();
    in.labels.put("Code-Review", (short) 2);
    RestResponse r =
        session.post("/changes/" + changeId + "/revisions/current/review",
            in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
  }

  protected void assertCherryPick(Git localGit, boolean contentMerge) throws IOException {
    assertRebase(localGit, contentMerge);
    RevCommit remoteHead = getRemoteHead();
    assertFalse(remoteHead.getFooterLines("Reviewed-On").isEmpty());
    assertFalse(remoteHead.getFooterLines("Reviewed-By").isEmpty());
  }

  protected void assertRebase(Git localGit, boolean contentMerge) throws IOException {
    Repository repo = localGit.getRepository();
    RevCommit localHead = getHead(repo);
    RevCommit remoteHead = getRemoteHead();
    assertNotEquals(localHead.getId(), remoteHead.getId());
    assertEquals(1, remoteHead.getParentCount());
    if (!contentMerge) {
      assertEquals(getLatestDiff(repo), getLatestRemoteDiff());
    }
    assertEquals(localHead.getShortMessage(), remoteHead.getShortMessage());
  }

  private RevCommit getHead(Repository repo) throws IOException {
    return getHead(repo, "HEAD");
  }

  protected RevCommit getRemoteHead() throws IOException {
    Repository repo = repoManager.openRepository(project);
    try {
      return getHead(repo, "refs/heads/master");
    } finally {
      repo.close();
    }
  }

  private RevCommit getHead(Repository repo, String name) throws IOException {
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        return rw.parseCommit(repo.getRef(name).getObjectId());
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  private String getLatestDiff(Repository repo) throws IOException {
    ObjectId oldTreeId = repo.resolve("HEAD~1^{tree}");
    ObjectId newTreeId = repo.resolve("HEAD^{tree}");
    return getLatestDiff(repo, oldTreeId, newTreeId);
  }

  private String getLatestRemoteDiff() throws IOException {
    Repository repo = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        ObjectId oldTreeId = repo.resolve("refs/heads/master~1^{tree}");
        ObjectId newTreeId = repo.resolve("refs/heads/master^{tree}");
        return getLatestDiff(repo, oldTreeId, newTreeId);
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  private String getLatestDiff(Repository repo, ObjectId oldTreeId,
      ObjectId newTreeId) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DiffFormatter fmt = new DiffFormatter(out);
    fmt.setRepository(repo);
    fmt.format(oldTreeId, newTreeId);
    fmt.flush();
    return out.toString();
  }
}
