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

package com.google.gerrit.acceptance.api.change;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ChangeIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GerritApi gApi;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private TestAccount user;

  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    user = accounts.user();
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    db = reviewDbProvider.open();
    atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
        identifiedUserFactory.create(Providers.of(db), admin.getId())));
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void abandon() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .abandon();
  }

  @Test
  public void restore() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .abandon();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .restore();
  }

  @Test
  public void revert() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .submit();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revert();
  }

  // Change is already up to date
  @Test(expected = ResourceConflictException.class)
  public void rebase() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .rebase();
  }

  @Test
  public void addReviewer() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .addReviewer(in);
  }

  private PushOneCommit.Result createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }
}
