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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

import java.io.IOException;

@NoHttpd
public class ChangeIT extends AbstractDaemonTest {

  @Test
  public void get() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    String triplet = "p~master~" + r.getChangeId();
    ChangeInfo c = info(triplet);
    assertEquals(triplet, c.id);
    assertEquals("p", c.project);
    assertEquals("master", c.branch);
    assertEquals(ChangeStatus.NEW, c.status);
    assertEquals("test commit", c.subject);
    assertEquals(true, c.mergeable);
    assertEquals(r.getChangeId(), c.changeId);
    assertEquals(c.created, c.updated);
    assertEquals(1, c._number);
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

  @Test
  public void createEmptyChange() throws RestApiException {
    ChangeInfo in = new ChangeInfo();
    in.branch = Constants.MASTER;
    in.subject = "Create a change from the API";
    in.project = project.get();
    ChangeInfo info = gApi
        .changes()
        .create(in)
        .get();
    assertEquals(in.project, info.project);
    assertEquals(in.branch, info.branch);
    assertEquals(in.subject, info.subject);
  }

  @Test
  public void testOtherBranchCommit_Positive() throws Exception {
    gApi.projects()
        .name(project.get())
        .branch("foo")
        .create(new BranchInput());
    createChange();
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    ChangeInfo info = get(push.to(git, "refs/for/foo").getChangeId());
    assertTrue(Iterables.getOnlyElement(info.revisions.values())
        .commit.otherBranchCommit);
  }

  @Test
  public void testOtherBranchCommit_Negative() throws Exception {
    assertFalse(Iterables.getOnlyElement(get(createChange()
        .getChangeId()).revisions.values())
        .commit.otherBranchCommit);
  }
}
