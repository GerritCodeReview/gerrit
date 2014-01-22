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

package com.google.gerrit.acceptance.api.revision;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
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

public class RevisionIT extends AbstractDaemonTest {

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

  private Git git;
  private ReviewDb db;
  private Project.NameKey project;

  @Before
  public void setUp() throws Exception {
    project = new Project.NameKey("p");
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
  public void reviewTriplet() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(approve());
  }

  @Test
  public void reviewCurrent() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(approve());
  }

  @Test
  public void reviewNumber() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(1)
        .review(approve());

    r = updateChange(r, "new content");
    gApi.changes()
        .id(r.getChangeId())
        .revision(2)
        .review(approve());
  }

  @Test
  public void submit() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .review(approve());
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .submit();
  }

  @Test
  public void deleteDraft() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createDraft();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .delete();
  }

  @Test
  public void cherryPick() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects()
        .name(project.get())
        .branch(in.destination)
        .create(new BranchInput());
    ChangeApi cApi = gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .cherryPick(in);
    cApi.current()
        .review(approve());
    cApi.current()
        .submit();
  }

  private PushOneCommit.Result createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(),
        "test commit", "a.txt", "some content");
    return push.to(git, "refs/for/master");
  }

  private PushOneCommit.Result updateChange(PushOneCommit.Result r,
      String content) throws GitAPIException, IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(),
        "test commit", "a.txt", content, r.getChangeId());
    return push.to(git, "refs/for/master");
  }

  private PushOneCommit.Result createDraft() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/drafts/master");
  }

  private static ReviewInput approve() {
    return new ReviewInput()
      .message("Looks good!")
      .label("Code-Review", 2);
  }
}
