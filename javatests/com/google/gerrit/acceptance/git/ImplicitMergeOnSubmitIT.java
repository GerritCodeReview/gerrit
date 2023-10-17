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
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for different commit graphs.
 *
 * The setup creates a repository with 3 branches: master, target1 and target2. All 3 branches
 * have common parent commit:
 * <pre>
 *   master  target1 target2 commit
 *   \         |           /
 *    -----base commit-----
 * </pre>
 *
 * Tests use MergeAlways submit strategy. All other submit strategies (except cherry pick) use
 * the same checks on submit. The {@link ImplicitMergeOnSubmitExperimentsIT} validates that
 * the implicit merge check is applied to all strategies (except cherry pick) and
 * {@link ImplicitMergeCherryPickIT} contains tests for the cherry pick startegy.
 */
public class ImplicitMergeOnSubmitIT extends AbstractImplicitMergeOnSubmit{
  private RevCommit masterTip;
  private RevCommit otherTip;
  RevCommit baseCommit;
  @Before
  public void setUp() throws Exception {
    setSubmitType(SubmitType.MERGE_ALWAYS);
    gApi.projects().name(project.get()).branch("other").create(new BranchInput());
    baseCommit = repo().parseCommit(ObjectId.fromString(gApi.projects().name(project.get()).branch("master").get().revision));
    masterTip = pushTo("refs/heads/master", ImmutableMap.of("master-file", "master-content"), baseCommit).getCommit();
    otherTip = pushTo("refs/heads/other", ImmutableMap.of("target-file", "target1-content"), baseCommit).getCommit();
  }

  @Test
  public void singleChangeImplicitMerge() throws Exception{
    /*
     * Single change which target to a different branch.
     * Commit graph:
     * <pre>
     *   master branch <- change c1
     * </pre>
     * The target branch for c1 is "target".
     */
    PushOneCommit.Result c1 = createApprovedChange("master", otherTip);
    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
  }

  @Test
  public void chainOfChangesImplicitMerge() throws Exception {
    PushOneCommit.Result c1 = createApprovedChange("master", otherTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2);
    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c2.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c3.getChangeId());
  }

  @Test
  public void chainOfChangesEndsWithExplicitMerge_onlyExplcitMergeCanBeSubmitted() throws Exception {
    PushOneCommit.Result c1 = createApprovedChange("master", otherTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2.getCommit(), masterTip);
    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c2.getChangeId());
    assertThatChangeSubmittable(c3.getChangeId());
  }

  @Test
  public void twoChainOfChangesSameTopic_oneChainImplicitMerge_rejectedOnSubmit() throws Exception {
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    PushOneCommit.Result c1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2.getCommit());
    PushOneCommit.Result i1 = createApprovedChange("master", otherTip);
    PushOneCommit.Result i2 = createApprovedChange("master", i1);
    PushOneCommit.Result i3 = createApprovedChange("master", i2.getCommit());
    gApi.changes().id(c1.getChangeId()).topic("test");
    gApi.changes().id(c2.getChangeId()).topic("test");
    gApi.changes().id(c3.getChangeId()).topic("test");
    gApi.changes().id(i1.getChangeId()).topic("test");
    gApi.changes().id(i2.getChangeId()).topic("test");
    gApi.changes().id(i3.getChangeId()).topic("test");

    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
  }

  @Test
  public void twoChainOfChangesDifferentBranchesSameTopic_oneChainImplicitMerge_rejectedOnSubmit() throws Exception {
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    PushOneCommit.Result c1 = createApprovedChange("other", masterTip);
    PushOneCommit.Result c2 = createApprovedChange("other", c1);
    PushOneCommit.Result c3 = createApprovedChange("other", c2.getCommit());
    PushOneCommit.Result i1 = createApprovedChange("master", masterTip);
    PushOneCommit.Result i2 = createApprovedChange("master", i1);
    PushOneCommit.Result i3 = createApprovedChange("master", i2.getCommit());
    gApi.changes().id(c1.getChangeId()).topic("test");
    gApi.changes().id(c2.getChangeId()).topic("test");
    gApi.changes().id(c3.getChangeId()).topic("test");
    gApi.changes().id(i1.getChangeId()).topic("test");
    gApi.changes().id(i2.getChangeId()).topic("test");
    gApi.changes().id(i3.getChangeId()).topic("test");

    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
  }

  @Test
  public void explicitMergeOnTopOfChain_onlyTopSubmittable() throws Exception {
    PushOneCommit.Result c1 = createApprovedChange("master", otherTip);
    PushOneCommit.Result c2 = createApprovedChange("master", c1);
    PushOneCommit.Result c3 = createApprovedChange("master", c2.getCommit());
    PushOneCommit.Result i3 = createApprovedChange("master", masterTip, c3.getCommit());

    assertSubmitRejectedWithImplicitMerge(c1.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c2.getChangeId());
    assertSubmitRejectedWithImplicitMerge(c3.getChangeId());
    assertThatChangeSubmittable(i3.getChangeId());
  }

  @Test
  public void threeBranches_onlyExplicitCommitSubmittable() throws Exception {
    BranchInput bi = new BranchInput();
    bi.revision = baseCommit.getName();
    gApi.projects().name(project.get()).branch("third").create(bi);
    RevCommit thirdBranchTip = pushTo("refs/heads/third", ImmutableMap.of("third-file", "third-content"), baseCommit).getCommit();


    PushOneCommit.Result c1 = createApprovedChange("master", masterTip, otherTip);
    PushOneCommit.Result c2 = createApprovedChange("master", otherTip, thirdBranchTip);
    PushOneCommit.Result c3 = createApprovedChange("master", c1, c2);

    assertSubmitRejectedWithImplicitMerge(c2.getChangeId());
    assertThatChangeSubmittable(c1.getChangeId());
    assertThatChangeSubmittable(c3.getChangeId());
  }

  private void assertSubmitRejectedWithImplicitMerge(String changeId) throws Exception {
    ResourceConflictException e = assertThrows(ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(e.getMessage()).contains("implicit merge");
  }

  private void assertThatChangeSubmittable(String chagneId) throws Exception {
    ChangeInfo ci = gApi.changes().id(chagneId).current().submit();
    assertThat(ci.submitted).isNotNull();
  }
}
