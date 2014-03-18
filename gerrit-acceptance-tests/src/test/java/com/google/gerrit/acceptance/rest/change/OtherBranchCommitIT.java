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

package com.google.gerrit.acceptance.rest.change;


import static com.google.gerrit.acceptance.GitUtil.checkout;
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static org.junit.Assert.*;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.InheritableBoolean;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.PutConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.io.IOException;

public class OtherBranchCommitIT extends AbstractDaemonTest {

  @Inject
  private GitRepositoryManager repoManager;


  @Test
  public void GetOtherBranchCommit() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result co = createChange(git, "master");
    String changeId = co.getChangeId();
    PatchSet ps = getCurrentPatchSet(changeId);
    PatchSetInfo info = new PatchSetInfo(ps.getId());

    assertEquals("other branch commit", info.getOtherBranchCommit(), false);
  }

  protected PushOneCommit.Result createChange(Git git, String branch)
      throws GitAPIException, IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/" + branch);
  }

  private PatchSet getCurrentPatchSet(String changeId) throws OrmException {
    return db.patchSets().get(
        Iterables.getOnlyElement(db.changes().byKey(new Change.Key(changeId)))
            .currentPatchSetId());
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


  protected Git createProject() throws JSchException, IOException,
      GitAPIException {
    return createProject(true);
  }

  private Git createProject(boolean emptyCommit) throws JSchException,
      IOException, GitAPIException {
    SshSession sshSession = new SshSession(server, admin);
    try {
      GitUtil.createProject(sshSession, project.get(), null, emptyCommit);
      setSubmitType(SubmitType.MERGE_IF_NECESSARY);
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

  public void createBranch(String project, String branch)
      throws GitAPIException, IOException, RestApiException {
    gApi.projects().name(project).branch(branch).create(new BranchInput());
  }
}
