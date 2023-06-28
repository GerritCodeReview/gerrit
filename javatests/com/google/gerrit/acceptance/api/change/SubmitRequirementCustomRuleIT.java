// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LegacySubmitRequirementInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SubmitRequirementCustomRuleIT extends AbstractDaemonTest {
  private static final LegacySubmitRequirement req =
      LegacySubmitRequirement.builder()
          .setType("custom_rule")
          .setFallbackText("Fallback text")
          .build();
  private static final LegacySubmitRequirementInfo reqInfo =
      new LegacySubmitRequirementInfo("NOT_READY", "Fallback text", "custom_rule");

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(SubmitRule.class)
            .annotatedWith(Exports.named("CustomSubmitRule"))
            .to(CustomSubmitRule.class);
      }
    };
  }

  @Inject private CustomSubmitRule rule;

  @Test
  public void submitRequirementIsPropagated() throws Exception {
    rule.block(false);
    PushOneCommit.Result r = createChange();

    ChangeInfo result = gApi.changes().id(r.getChangeId()).get();
    assertThat(result.requirements).isEmpty();

    rule.block(true);
    result = gApi.changes().id(r.getChangeId()).get();
    assertThat(result.requirements).containsExactly(reqInfo);
  }

  @Test
  public void submitRequirementIsPropagatedInQuery() throws Exception {
    rule.block(false);
    PushOneCommit.Result r = createChange();

    String query = "status:open project:" + project.get();
    List<ChangeInfo> result = gApi.changes().query(query).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).requirements).isEmpty();

    // Submit rule behavior is changed, but the query still returns
    // the previous result from the index
    rule.block(true);
    result = gApi.changes().query(query).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).requirements).isEmpty();

    // The submit rule result is updated after the change is reindexed
    gApi.changes().id(r.getChangeId()).index();
    result = gApi.changes().query(query).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).requirements).containsExactly(reqInfo);
  }

  @Test
  public void submittableQueryRuleNotReady() throws Exception {
    ChangeApi change = newChangeApi();

    // Satisfy the default rule.
    approveChange(change);

    // The custom rule is NOT_READY.
    rule.block(true);
    change.index();

    assertThat(queryIsSubmittable()).isEmpty();
  }

  @Test
  public void submittableQueryRuleError() throws Exception {
    ChangeApi change = newChangeApi();

    // Satisfy the default rule.
    approveChange(change);

    rule.status(Optional.of(SubmitRecord.Status.RULE_ERROR));
    change.index();

    assertThat(queryIsSubmittable()).isEmpty();
  }

  @Test
  public void submittableQueryDefaultRejected() throws Exception {
    ChangeApi change = newChangeApi();

    // CodeReview:-2 the change, causing the default rule to fail.
    rejectChange(change);

    rule.status(Optional.of(SubmitRecord.Status.OK));
    change.index();

    assertThat(queryIsSubmittable()).isEmpty();
  }

  @Test
  public void submittableQueryRuleOk() throws Exception {
    ChangeApi change = newChangeApi();

    // Satisfy the default rule.
    approveChange(change);

    rule.status(Optional.of(SubmitRecord.Status.OK));
    newChangeApi(change.id()).index();

    List<ChangeInfo> result = queryIsSubmittable();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).changeId).isEqualTo(change.info().changeId);
  }

  @Test
  public void submittableQueryRuleNoRecord() throws Exception {
    ChangeApi change = newChangeApi();

    // Satisfy the default rule.
    approveChange(change);

    // Our custom rule isn't providing any submit records.
    rule.status(Optional.empty());
    newChangeApi(change.id()).index();

    // is:submittable should return the change, since it was approved and the custom rule is not
    // blocking it.
    List<ChangeInfo> result = queryIsSubmittable();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).changeId).isEqualTo(change.info().changeId);
  }

  @Test
  public void submitRuleIsInvokedOnlyOnceWhenGettingChangeDetails() throws Exception {
    PushOneCommit.Result r = createChange("Some Change", "foo.txt", "some content");
    String changeId = r.getChangeId();

    rule.numberOfEvaluations.set(0);
    gApi.changes()
        .id(changeId)
        .get(ListChangesOption.ALL_REVISIONS, ListChangesOption.CURRENT_ACTIONS);

    // Submit rules are computed freshly, but only once.
    assertThat(rule.numberOfEvaluations.get()).isEqualTo(1);
  }

  @Test
  public void submitRuleIsNotInvokedWhenQueryingChange() throws Exception {
    PushOneCommit.Result r = createChange("Some Change", "foo.txt", "some content");
    String changeId = r.getChangeId();

    rule.numberOfEvaluations.set(0);
    gApi.changes()
        .query(changeId)
        .withOptions(ListChangesOption.ALL_REVISIONS, ListChangesOption.CURRENT_ACTIONS)
        .get();

    // Submit rule evaluation results from the change index are reused
    assertThat(rule.numberOfEvaluations.get()).isEqualTo(0);
  }

  private List<ChangeInfo> queryIsSubmittable() throws Exception {
    return gApi.changes().query("is:submittable project:" + project.get()).get();
  }

  private ChangeApi newChangeApi() throws Exception {
    return gApi.changes().id(createChange().getChangeId());
  }

  private ChangeApi newChangeApi(String changeId) throws Exception {
    return gApi.changes().id(changeId);
  }

  private void approveChange(ChangeApi changeApi) throws Exception {
    changeApi.current().review(ReviewInput.approve());
  }

  private void rejectChange(ChangeApi changeApi) throws Exception {
    changeApi.current().review(ReviewInput.reject());
  }

  @Singleton
  private static class CustomSubmitRule implements SubmitRule {
    private Optional<SubmitRecord.Status> recordStatus = Optional.empty();
    private AtomicInteger numberOfEvaluations = new AtomicInteger();

    public void block(boolean block) {
      this.status(block ? Optional.of(SubmitRecord.Status.NOT_READY) : Optional.empty());
    }

    public void status(Optional<SubmitRecord.Status> status) {
      this.recordStatus = status;
    }

    @Override
    public Optional<SubmitRecord> evaluate(ChangeData changeData) {
      numberOfEvaluations.incrementAndGet();
      if (this.recordStatus.isPresent()) {
        SubmitRecord record = new SubmitRecord();
        record.labels = new ArrayList<>();
        record.status = this.recordStatus.get();
        record.requirements = ImmutableList.of(req);
        return Optional.of(record);
      }
      return Optional.empty();
    }
  }
}
