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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@NoHttpd
public class ChangeIT extends AbstractDaemonTest {

  @Test
  public void get() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = "p~master~" + r.getChangeId();
    ChangeInfo c = info(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.project).isEqualTo("p");
    assertThat(c.branch).isEqualTo("master");
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);
    assertThat(c.subject).isEqualTo("test commit");
    assertThat(c.mergeable).isTrue();
    assertThat(c.changeId).isEqualTo(r.getChangeId());
    assertThat(c.created).isEqualTo(c.updated);
    assertThat(c._number).is(1);

    assertThat(c.owner._accountId).is(admin.getId().get());
    assertThat(c.owner.name).isNull();
    assertThat(c.owner.email).isNull();
    assertThat(c.owner.username).isNull();
    assertThat(c.owner.avatars).isNull();
  }

  @Test
  public void abandon() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes()
        .id(r.getChangeId())
        .abandon();
    ChangeInfo info = get(r.getChangeId());
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase())
        .contains("abandoned");
  }

  @Test
  public void restore() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .abandon();
    gApi.changes()
        .id(r.getChangeId())
        .restore();
  }

  @Test
  public void revert() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();
    gApi.changes()
        .id(r.getChangeId())
        .revert();
  }

  // Change is already up to date
  @Test(expected = ResourceConflictException.class)
  public void rebase() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .rebase();
  }

  private Set<Account.Id> getReviewers(String changeId) throws Exception {
    ChangeInfo ci = gApi.changes().id(changeId).get();
    Set<Account.Id> result = Sets.newHashSet();
    for (LabelInfo li : ci.labels.values()) {
      for (ApprovalInfo ai : li.all) {
        result.add(new Account.Id(ai._accountId));
      }
    }
    return result;
  }

  @Test
  public void addReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    assertThat((Iterable<?>)getReviewers(r.getChangeId()))
        .containsExactlyElementsIn(ImmutableSet.of(user.id));
  }

  @Test
  public void addReviewerToClosedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();

    assertThat((Iterable<?>)getReviewers(r.getChangeId()))
      .containsExactlyElementsIn(ImmutableSet.of(admin.getId()));

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);
    assertThat((Iterable<?>)getReviewers(r.getChangeId()))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId(), user.id));
  }

  @Test
  public void createEmptyChange() throws Exception {
    ChangeInfo in = new ChangeInfo();
    in.branch = Constants.MASTER;
    in.subject = "Create a change from the API";
    in.project = project.get();
    ChangeInfo info = gApi
        .changes()
        .create(in)
        .get();
    assertThat(info.project).isEqualTo(in.project);
    assertThat(info.branch).isEqualTo(in.branch);
    assertThat(info.subject).isEqualTo(in.subject);
  }

  @Test
  public void queryChangesNoQuery() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = gApi.changes().query().get();
    assertThat(results).hasSize(2);
    assertThat(results.get(0).changeId).isEqualTo(r2.getChangeId());
    assertThat(results.get(1).changeId).isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesNoResults() throws Exception {
    createChange();
    List<ChangeInfo> results = query("status:open");
    assertThat(results).hasSize(1);
    results = query("status:closed");
    assertThat(results).isEmpty();
  }

  @Test
  public void queryChangesOneTerm() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = query("status:open");
    assertThat(results).hasSize(2);
    assertThat(results.get(0).changeId).isEqualTo(r2.getChangeId());
    assertThat(results.get(1).changeId).isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesMultipleTerms() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results = query("status:open " + r1.getChangeId());
    assertThat(Iterables.getOnlyElement(results).changeId)
        .isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesLimit() throws Exception {
    createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = gApi.changes().query().withLimit(1).get();
    assertThat(results).hasSize(1);
    assertThat(Iterables.getOnlyElement(results).changeId)
        .isEqualTo(r2.getChangeId());
  }

  @Test
  public void queryChangesStart() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results = gApi.changes().query().withStart(1).get();
    assertThat(Iterables.getOnlyElement(results).changeId)
        .isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesNoOptions() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo result = Iterables.getOnlyElement(query(r.getChangeId()));
    assertThat(result.labels).isNull();
    assertThat((Iterable<?>)result.messages).isNull();
    assertThat(result.revisions).isNull();
    assertThat(result.actions).isNull();
  }

  @Test
  public void queryChangesOptions() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo result = Iterables.getOnlyElement(gApi.changes()
        .query(r.getChangeId())
        .withOptions(EnumSet.allOf(ListChangesOption.class))
        .get());
    assertThat(Iterables.getOnlyElement(result.labels.keySet()))
        .isEqualTo("Code-Review");
    assertThat((Iterable<?>)result.messages).hasSize(1);
    assertThat(result.actions).isNotEmpty();

    RevisionInfo rev = Iterables.getOnlyElement(result.revisions.values());
    assertThat(rev._number).isEqualTo(r.getPatchSetId().get());
    assertThat(rev.ref).isEqualTo(r.getPatchSetId().toRefName());
    assertThat(rev.actions).isNotEmpty();
  }

  @Test
  public void queryChangesOwnerWithDifferentUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(Iterables.getOnlyElement(query("owner:self")).changeId)
        .isEqualTo(r.getChangeId());
    setApiUser(user);
    assertThat(query("owner:self")).isEmpty();
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
    assertThat(get(r.getChangeId()).reviewed).isNull();

    revision(r).review(ReviewInput.recommend());
    assertThat(get(r.getChangeId()).reviewed).isTrue();
  }

  @Test
  public void topic() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .topic()).isEqualTo("");
    gApi.changes()
        .id(r.getChangeId())
        .topic("mytopic");
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .topic()).isEqualTo("mytopic");
    gApi.changes()
        .id(r.getChangeId())
        .topic("");
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .topic()).isEqualTo("");
  }

  @Test
  public void check() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .get()
        .problems).isNull();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .get(EnumSet.of(ListChangesOption.CHECK))
        .problems).isEmpty();
  }
}
