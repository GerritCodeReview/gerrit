// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that gerrit correctly detects implicit merges on submit..
 *
 * <p>The setup creates a repository with 2 branches: master and target. Both branches have common
 * parent:
 *
 * <pre>{@code
 * master                target
 *  |                       |
 *  ----->base commit <------
 * }</pre>
 *
 * Tests use only MergeAlways strategy. All other submit strategies (except cherry pick and rebase
 * always) use the same checks on submit. The {@link ImplicitMergeOnSubmitExperimentsIT} validates
 * that the implicit merge check is applied to all strategies (except cherry pick and rebase always)
 * and {@link ImplicitMergeOnSubmitByCherryPickOrRebaseAlwaysIT} contains tests for the cherry pick
 * and rebase always strategies.
 */
public class ImplicitMergeOnSubmitIT extends AbstractImplicitMergeTest {
  private RevCommit masterTip;
  private RevCommit otherTip;
  RevCommit baseCommit;

  @Before
  public void setUp() throws Exception {
    setSubmitType(SubmitType.MERGE_ALWAYS);
    gApi.projects().name(project.get()).branch("other").create(new BranchInput());
    baseCommit =
        repo()
            .parseCommit(
                ObjectId.fromString(
                    gApi.projects().name(project.get()).branch("master").get().revision));
    masterTip =
        pushTo("refs/heads/master", ImmutableMap.of("master-file", "master-content"), baseCommit)
            .getCommit();
    otherTip =
        pushTo("refs/heads/other", ImmutableMap.of("target-file", "target1-content"), baseCommit)
            .getCommit();
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void singleChangeImplicitMerge() throws Exception {
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void chainOfChangesImplicitMerge() throws Exception {
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    PushOneCommit.Result c1 = createApprovedChange("master", implicitMerge);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c2.getChangeId());
  }

  @Test
  public void chainOfChangesOnTopOfTargetBranchTipNoImplicitMerge() throws Exception {
    PushOneCommit.Result c1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2);
    assertThatChangeSubmittable(c1.getChangeId());
    assertThatChangeSubmittable(c2.getChangeId());
    assertThatChangeSubmittable(c3.getChangeId());
  }

  @Test
  public void chainOfChangesNotOnTopOfTargetBranchTipNoImplicitMerge() throws Exception {
    // Add one more commit to master branch.
    pushTo("refs/heads/master", ImmutableMap.of(), masterTip);
    PushOneCommit.Result c1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2);
    assertThatChangeSubmittable(c1.getChangeId());
    assertThatChangeSubmittable(c2.getChangeId());
    assertThatChangeSubmittable(c3.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void chainOfChangesNotOnTopOfTargetBranchTipWithImplicitMerge() throws Exception {
    // Add one more commit to master branch.
    pushTo("refs/heads/master", ImmutableMap.of(), masterTip);
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    PushOneCommit.Result c2 = createApprovedChange("master", implicitMerge);
    PushOneCommit.Result c3 = createApprovedChange("master", c2);
    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c2.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c3.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void chainOfChangesEndsWithExplicitMerge_onlyExplcitMergeCanBeSubmitted()
      throws Exception {
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    PushOneCommit.Result changeInChange = createApprovedChange("master", implicitMerge);
    PushOneCommit.Result explicitMerge =
        createApprovedChange("master", changeInChange.getCommit(), masterTip);
    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
    assertSubmitRejectedWithImplicitMerge(changeInChange.getChangeId());
    assertThatChangeSubmittable(explicitMerge.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void twoChainOfChangesSameTopic_oneChainImplicitMerge_rejectedOnSubmit() throws Exception {
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    PushOneCommit.Result c1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2.getCommit());
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    PushOneCommit.Result im1 = createApprovedChange("master", implicitMerge);
    PushOneCommit.Result im2 = createApprovedChange("master", im1.getCommit());
    // The AbstractDaemonTest doesn't fully reset gerrit; it creates a new project for each test
    // and doesn't remove changes created in tests. As a result, if the same topic is used in
    // several tests gerrit tries to submit all changes, including changes from other tests.
    // The name method returns name scoped to this test method .
    String topic = name("topic");
    gApi.changes().id(c1.getChangeId()).topic(topic);
    gApi.changes().id(c2.getChangeId()).topic(topic);
    gApi.changes().id(c3.getChangeId()).topic(topic);
    gApi.changes().id(implicitMerge.getChangeId()).topic(topic);
    gApi.changes().id(im1.getChangeId()).topic(topic);
    gApi.changes().id(im2.getChangeId()).topic(topic);

    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
  }

  @Test
  public void twoChainOfChangesSameTopic_noImplicitMerge_canSubmit() throws Exception {
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    PushOneCommit.Result chain1change1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result chain1change2 = createApprovedChange("master", chain1change1);
    PushOneCommit.Result chain1change3 = createApprovedChange("master", chain1change2);
    PushOneCommit.Result chain2change1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result chain2change2 = createApprovedChange("master", chain2change1);
    PushOneCommit.Result chain2change3 = createApprovedChange("master", chain2change2);
    // The AbstractDaemonTest doesn't fully reset gerrit; it creates a new project for each test
    // and doesn't remove changes created in tests. As a result, if the same topic is used in
    // several tests gerrit tries to submit all changes, including changes from other tests.
    // The name method returns name scoped to this test method .
    String topic = name("topic");
    gApi.changes().id(chain1change1.getChangeId()).topic(topic);
    gApi.changes().id(chain1change2.getChangeId()).topic(topic);
    gApi.changes().id(chain1change3.getChangeId()).topic(topic);
    gApi.changes().id(chain2change1.getChangeId()).topic(topic);
    gApi.changes().id(chain2change2.getChangeId()).topic(topic);
    gApi.changes().id(chain2change3.getChangeId()).topic(topic);

    assertThatChangeSubmittable(chain1change1.getChangeId());
  }

  @Test
  public void twoChainOfChangesSameTopicNotOnTopOfBranch_noImplicitMerge_canSubmit()
      throws Exception {
    // Add one more commit to master branch.
    pushTo("refs/heads/master", ImmutableMap.of(), masterTip);
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    PushOneCommit.Result chain1change1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result chain1change2 = createApprovedChange("master", chain1change1);
    PushOneCommit.Result chain1change3 = createApprovedChange("master", chain1change2);
    PushOneCommit.Result chain2change1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result chain2change2 = createApprovedChange("master", chain2change1);
    PushOneCommit.Result chain2change3 = createApprovedChange("master", chain2change2);
    // The AbstractDaemonTest doesn't fully reset gerrit; it creates a new project for each test
    // and doesn't remove changes created in tests. As a result, if the same topic is used in
    // several tests gerrit tries to submit all changes, including changes from other tests.
    // The name method returns name scoped to this test method .
    String topic = name("topic");
    gApi.changes().id(chain1change1.getChangeId()).topic(topic);
    gApi.changes().id(chain1change2.getChangeId()).topic(topic);
    gApi.changes().id(chain1change3.getChangeId()).topic(topic);
    gApi.changes().id(chain2change1.getChangeId()).topic(topic);
    gApi.changes().id(chain2change2.getChangeId()).topic(topic);
    gApi.changes().id(chain2change3.getChangeId()).topic(topic);

    assertThatChangeSubmittable(chain1change1.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void twoChainOfChangesEndsWithExplicitMergeSameTopicNotTipOfBranches_canBeSubmitted()
      throws Exception {
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    // Add one more commit to master branch.
    pushTo("refs/heads/master", ImmutableMap.of(), masterTip);
    PushOneCommit.Result implicitMerge1 = createApprovedChange("master", otherTip);
    PushOneCommit.Result changeInChain1 = createApprovedChange("master", implicitMerge1);
    PushOneCommit.Result explicitMerge1 =
        createApprovedChange("master", changeInChain1.getCommit(), masterTip);
    PushOneCommit.Result implicitMerge2 = createApprovedChange("master", otherTip);
    PushOneCommit.Result changeInChain2 = createApprovedChange("master", implicitMerge2);
    PushOneCommit.Result explicitMerge2 =
        createApprovedChange("master", changeInChain2.getCommit(), masterTip);
    // The AbstractDaemonTest doesn't fully reset gerrit; it creates a new project for each test
    // and doesn't remove changes created in tests. As a result, if the same topic is used in
    // several tests gerrit tries to submit all changes, including changes from other tests.
    // The name method returns name scoped to this test method .
    String topic = name("topic");
    gApi.changes().id(implicitMerge1.getChangeId()).topic(topic);
    gApi.changes().id(changeInChain1.getChangeId()).topic(topic);
    gApi.changes().id(explicitMerge1.getChangeId()).topic(topic);
    gApi.changes().id(implicitMerge2.getChangeId()).topic(topic);
    gApi.changes().id(changeInChain2.getChangeId()).topic(topic);
    gApi.changes().id(explicitMerge2.getChangeId()).topic(topic);

    assertThatChangeSubmittable(explicitMerge2.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void twoChainOfChangesDifferentBranchesSameTopic_oneChainImplicitMerge_rejectedOnSubmit()
      throws Exception {
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    PushOneCommit.Result implicitMerge = createApprovedChange("other", masterTip);
    PushOneCommit.Result im1 = createApprovedChange("other", implicitMerge);
    PushOneCommit.Result im2 = createApprovedChange("other", im1.getCommit());
    PushOneCommit.Result c1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2.getCommit());
    // The AbstractDaemonTest doesn't fully reset gerrit; it creates a new project for each test
    // and doesn't remove changes created in tests. As a result, if the same topic is used in
    // several tests gerrit tries to submit all changes, including changes from other tests.
    // The name method returns name scoped to this test method .
    String topic = name("topic");
    gApi.changes().id(implicitMerge.getChangeId()).topic(topic);
    gApi.changes().id(im1.getChangeId()).topic(topic);
    gApi.changes().id(im2.getChangeId()).topic(topic);
    gApi.changes().id(c1.getChangeId()).topic(topic);
    gApi.changes().id(c2.getChangeId()).topic(topic);
    gApi.changes().id(c3.getChangeId()).topic(topic);

    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void explicitMergeOnTopOfChain_onlyTopSubmittable() throws Exception {
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    PushOneCommit.Result im1 = createApprovedChange("master", implicitMerge);
    PushOneCommit.Result im2 = createApprovedChange("master", im1.getCommit());
    PushOneCommit.Result explicitMerge = createApprovedChange("master", masterTip, im2.getCommit());

    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
    assertSubmitRejectedWithImplicitMerge(im1.getChangeId());
    assertSubmitRejectedWithImplicitMerge(im2.getChangeId());
    assertThatChangeSubmittable(explicitMerge.getChangeId());
  }

  @Test
  @GerritConfig(name = "repository.*.defaultConfig", value = "receive.rejectImplicitMerges=false")
  public void explicitMergeOnTopOfChainParentIsNotBranchTip_onlyTopSubmittable() throws Exception {
    // Add one more commit to master and other branches.
    pushTo("refs/heads/master", ImmutableMap.of(), masterTip);
    pushTo("refs/heads/other", ImmutableMap.of(), otherTip);

    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip);
    PushOneCommit.Result im1 = createApprovedChange("master", implicitMerge);
    PushOneCommit.Result im2 = createApprovedChange("master", im1.getCommit());
    PushOneCommit.Result explicitMerge = createApprovedChange("master", masterTip, im2.getCommit());

    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
    assertSubmitRejectedWithImplicitMerge(im1.getChangeId());
    assertSubmitRejectedWithImplicitMerge(im2.getChangeId());
    assertThatChangeSubmittable(explicitMerge.getChangeId());
  }

  @Test
  public void threeBranches_onlyExplicitCommitSubmittable() throws Exception {
    BranchInput bi = new BranchInput();
    bi.revision = baseCommit.getName();
    gApi.projects().name(project.get()).branch("third").create(bi);
    RevCommit thirdBranchTip =
        pushTo("refs/heads/third", ImmutableMap.of("third-file", "third-content"), baseCommit)
            .getCommit();

    PushOneCommit.Result explicitMerge = createApprovedChange("master", masterTip, otherTip);
    PushOneCommit.Result implicitMerge = createApprovedChange("master", otherTip, thirdBranchTip);
    PushOneCommit.Result explicitMerge2 =
        createApprovedChange("master", explicitMerge, implicitMerge);

    assertSubmitRejectedWithImplicitMerge(implicitMerge.getChangeId());
    assertThatChangeSubmittable(explicitMerge.getChangeId());
    assertThatChangeSubmittable(explicitMerge2.getChangeId());
  }

  private void assertSubmitRejectedWithImplicitMerge(String changeId) throws Exception {
    ResourceConflictException e =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(e.getMessage()).contains("implicit merge");
  }

  private void assertThatChangeSubmittable(String changeId) throws Exception {
    ChangeInfo ci = gApi.changes().id(changeId).current().submit();
    assertThat(ci.submitted).isNotNull();
  }
}
