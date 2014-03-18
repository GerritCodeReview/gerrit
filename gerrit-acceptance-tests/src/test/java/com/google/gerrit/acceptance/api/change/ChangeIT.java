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

import static com.google.gerrit.acceptance.GitUtil.add;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.checkout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil.Commit;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

  private static Set<Account.Id> getReviewers(ChangeInfo ci) {
    Set<Account.Id> result = Sets.newHashSet();
    for (LabelInfo li : ci.labels.values()) {
      for (ApprovalInfo ai : li.all) {
        result.add(new Account.Id(ai._accountId));
      }
    }
    return result;
  }

  @Test
  public void addReviewer() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    ChangeApi cApi = gApi.changes().id("p~master~" + r.getChangeId());
    cApi.addReviewer(in);
    assertEquals(ImmutableSet.of(user.id), getReviewers(cApi.get()));
  }

  @Test
  public void addReviewerToClosedChange() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = createChange();
    ChangeApi cApi = gApi.changes().id("p~master~" + r.getChangeId());
    cApi.revision(r.getCommit().name())
        .review(ReviewInput.approve());
    cApi.revision(r.getCommit().name())
        .submit();

    assertEquals(ImmutableSet.of(admin.getId()), getReviewers(cApi.get()));

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id("p~master~" + r.getChangeId())
        .addReviewer(in);
    assertEquals(ImmutableSet.of(admin.getId(), user.id),
        getReviewers(cApi.get()));
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
  public void queryChangesNoQuery() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = gApi.changes().query().get();
    assertEquals(2, results.size());
    assertEquals(r2.getChangeId(), results.get(0).changeId);
    assertEquals(r1.getChangeId(), results.get(1).changeId);
  }

  @Test
  public void queryChangesNoResults() throws Exception {
    createChange();
    List<ChangeInfo> results = query("status:open");
    assertEquals(1, results.size());
    results = query("status:closed");
    assertTrue(results.isEmpty());
  }

  @Test
  public void queryChangesOneTerm() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = query("status:open");
    assertEquals(2, results.size());
    assertEquals(r2.getChangeId(), results.get(0).changeId);
    assertEquals(r1.getChangeId(), results.get(1).changeId);
  }

  @Test
  public void queryChangesMultipleTerms() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results = query("status:open " + r1.getChangeId());
    assertEquals(r1.getChangeId(), Iterables.getOnlyElement(results).changeId);
  }

  @Test
  public void queryChangesLimit() throws Exception {
    createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = gApi.changes().query().withLimit(1).get();
    assertEquals(1, results.size());
    assertEquals(r2.getChangeId(), Iterables.getOnlyElement(results).changeId);
  }

  @Test
  public void queryChangesStart() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results = gApi.changes().query().withStart(1).get();
    assertEquals(r1.getChangeId(), Iterables.getOnlyElement(results).changeId);
  }

  @Test
  public void queryChangesNoOptions() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo result = Iterables.getOnlyElement(query(r.getChangeId()));
    assertNull(result.labels);
    assertNull(result.messages);
    assertNull(result.revisions);
    assertNull(result.actions);
  }

  @Test
  public void queryChangesOptions() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo result = Iterables.getOnlyElement(gApi.changes()
        .query(r.getChangeId())
        .withOptions(EnumSet.allOf(ListChangesOption.class))
        .get());
    assertEquals("Code-Review",
        Iterables.getOnlyElement(result.labels.keySet()));
    assertEquals(1, result.messages.size());
    assertFalse(result.actions.isEmpty());

    RevisionInfo rev = Iterables.getOnlyElement(result.revisions.values());
    assertEquals(r.getPatchSetId().get(), rev._number);
    assertFalse(rev.actions.isEmpty());
  }

  @Test
  public void queryChangesOwnerWithDifferentUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    assertEquals(r.getChangeId(),
        Iterables.getOnlyElement(query("owner:self")).changeId);
    setApiUser(user);
    assertTrue(query("owner:self").isEmpty());
  }

  @Test
  public void checkReviewedFlagBeforeAndAfterReview() throws Exception {
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    setApiUser(user);
    assertNull(get(r.getChangeId()).reviewed);

    revision(r).review(ReviewInput.recommend());
    assertTrue(get(r.getChangeId()).reviewed);
  }

  @Test
  public void testOtherBranchCommit_Positive() throws Exception {
    gApi.projects()
        .name(project.get())
        .branch("foo")
        .create(new BranchInput());
    add(git, "foo", "foo");
    Commit c = createCommit(git, admin.getIdent(), "foo");
    pushHead(git, "refs/heads/master", false);
    checkout(git, c.getCommit().name());
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
