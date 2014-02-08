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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

@NoHttpd
public class RevisionIT extends AbstractDaemonTest {

  private TestAccount admin2;

  @Before
  public void setUp() throws Exception {
    admin2 = accounts.admin2();
  }

  @Test
  public void reviewTriplet() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewCurrent() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewNumber() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(1)
        .review(ReviewInput.approve());

    r = updateChange(r, "new content");
    gApi.changes()
        .id(r.getChangeId())
        .revision(2)
        .review(ReviewInput.approve());
  }

  @Test
  public void submit() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .submit();
  }

  @Test(expected = AuthException.class)
  public void submitOnBehalfOf() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email;
    in.waitForMerge = true;
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .current()
        .submit(in);
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
        .review(ReviewInput.approve());
    cApi.current()
        .submit();
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
}
