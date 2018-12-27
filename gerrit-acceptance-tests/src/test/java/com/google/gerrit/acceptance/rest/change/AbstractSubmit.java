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

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.extensions.common.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.common.ListChangesOption.DETAILED_LABELS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.ChangeJson.LabelInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.PutConfig;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
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
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSubmit extends AbstractDaemonTest {

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private ChangeNotes.Factory notesFactory;

  @Inject
  private ApprovalsUtil approvalsUtil;


  @Before
  public void setUp() throws Exception {
    project = new Project.NameKey("p2");
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
    PutConfig.Input in = new PutConfig.Input();
    in.submitType = submitType;
    in.useContentMerge = InheritableBoolean.FALSE;
    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/config", in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
  }

  protected void setUseContentMerge() throws IOException {
    PutConfig.Input in = new PutConfig.Input();
    in.useContentMerge = InheritableBoolean.TRUE;
    RestResponse r =
        adminSession.put("/projects/" + project.get() + "/config", in);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
  }

  protected PushOneCommit.Result createChange(Git git) throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }

  protected PushOneCommit.Result createChange(Git git, String subject,
      String fileName, String content) throws GitAPIException, IOException {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), subject, fileName, content);
    return push.to(git, "refs/for/master");
  }

  protected void submit(String changeId) throws IOException {
    submit(changeId, HttpStatus.SC_OK);
  }

  protected void submitWithConflict(String changeId) throws IOException {
    submit(changeId, HttpStatus.SC_CONFLICT);
  }

  protected void submitStatusOnly(String changeId)
      throws IOException, OrmException {
    approve(changeId);
    Change c = db.changes().byKey(new Change.Key(changeId)).toList().get(0);
    c.setStatus(Change.Status.SUBMITTED);
    db.changes().update(Collections.singleton(c));
    db.patchSetApprovals().insert(Collections.singleton(
        new PatchSetApproval(
            new PatchSetApproval.Key(
                c.currentPatchSetId(),
                admin.id,
                PatchSetApproval.LabelId.SUBMIT),
            (short) 1,
            new Timestamp(System.currentTimeMillis()))));
  }

  private void submit(String changeId, int expectedStatus) throws IOException {
    approve(changeId);
    SubmitInput subm = new SubmitInput();
    subm.waitForMerge = true;
    RestResponse r =
        adminSession.post("/changes/" + changeId + "/submit", subm);
    assertEquals(expectedStatus, r.getStatusCode());
    if (expectedStatus == HttpStatus.SC_OK) {
      ChangeInfo change =
          newGson().fromJson(r.getReader(),
              new TypeToken<ChangeInfo>() {}.getType());
      assertEquals(Change.Status.MERGED, change.status);
    }
    r.consume();
  }

  private void approve(String changeId) throws IOException {
    RestResponse r = adminSession.post(
        "/changes/" + changeId + "/revisions/current/review",
        new ReviewInput().label("Code-Review", 2));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    r.consume();
  }

  protected void assertCurrentRevision(String changeId, int expectedNum,
      ObjectId expectedId) throws IOException {
    ChangeInfo c = getChange(changeId, CURRENT_REVISION);
    assertEquals(expectedId.name(), c.currentRevision);
    assertEquals(expectedNum, c.revisions.get(expectedId.name())._number);
  }

  protected void assertApproved(String changeId) throws IOException {
    ChangeInfo c = getChange(changeId, DETAILED_LABELS);
    LabelInfo cr = c.labels.get("Code-Review");
    assertEquals(1, cr.all.size());
    assertEquals(2, cr.all.get(0).value.intValue());
    assertEquals("Administrator", cr.all.get(0).name);
  }

  protected void assertSubmitter(String changeId, int psId)
      throws OrmException, IOException {
    ChangeNotes cn = notesFactory.create(
        Iterables.getOnlyElement(db.changes().byKey(new Change.Key(changeId))));
    PatchSetApproval submitter = approvalsUtil.getSubmitter(
        db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertTrue(submitter.isSubmit());
    assertEquals(admin.getId(), submitter.getAccountId());
  }

  protected void assertCherryPick(Git localGit, boolean contentMerge)
      throws IOException {
    assertRebase(localGit, contentMerge);
    RevCommit remoteHead = getRemoteHead();
    assertFalse(remoteHead.getFooterLines("Reviewed-On").isEmpty());
    assertFalse(remoteHead.getFooterLines("Reviewed-By").isEmpty());
  }

  protected void assertRebase(Git localGit, boolean contentMerge)
      throws IOException {
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

  protected List<RevCommit> getRemoteLog() throws IOException {
    Repository repo = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        rw.markStart(rw.parseCommit(
            repo.getRef("refs/heads/master").getObjectId()));
        return Lists.newArrayList(rw);
      } finally {
        rw.close();
      }
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
        rw.close();
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
        rw.close();
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
