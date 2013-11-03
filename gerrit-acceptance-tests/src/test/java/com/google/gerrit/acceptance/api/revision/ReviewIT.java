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

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwt.thirdparty.guava.common.collect.Maps;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ReviewIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GerritApi gApi;

  @Inject
  AcceptanceTestRequestScope atrScope;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  private TestAccount admin;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    initSsh(admin);
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
        identifiedUserFactory.create(admin.getId())));
    db = reviewDbProvider.open();
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
        .review(makeReview());
  }

  @Test
  public void reviewId() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(makeReview());
  }

  private PushOneCommit.Result createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }

  private static ReviewInput makeReview() {
    ReviewInput in = new ReviewInput();
    in.message = "Looks good!";
    in.labels = Maps.newHashMap();
    in.labels.put("Code-Review", (short) 2);
    return in;
  }
}
