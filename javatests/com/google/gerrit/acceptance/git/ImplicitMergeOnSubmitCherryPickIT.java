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
import static com.google.gerrit.server.util.CommitMessageUtil.generateChangeId;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests how implicit merges are submitted by the cherry pick strategy.
 *
 * <p>Verifies that implicit merges can be submitted and that they doesn't add content from the
 * implicitly merged branch to the target branch.
 */
public class ImplicitMergeOnSubmitCherryPickIT extends AbstractImplicitMergeTest {

  private String implicitMergeChangeId;

  @Before
  public void setUp() throws Exception {
    setRejectImplicitMerges(false);
    setSubmitType(SubmitType.CHERRY_PICK);
    RevCommit base = repo().parseCommit(repo().exactRef("HEAD").getObjectId());
    RevCommit masterBranchTip =
        pushTo("refs/heads/master", ImmutableMap.of("master-content", "master-first-line\n"), base)
            .getCommit();
    RevCommit stableBranchTip =
        pushTo("refs/heads/stable", ImmutableMap.of("stable-content", "stable-first-line\n"), base)
            .getCommit();
    implicitMergeChangeId = "I" + generateChangeId().name();
    RevCommit implicitMergeChange =
        createChangeWithoutPush(
            implicitMergeChangeId,
            ImmutableMap.of("master-content2", "added-by-implicit-merge\n"),
            masterBranchTip);
    String explicitMergeChangeId =
        pushTo(
                "refs/for/stable",
                ImmutableMap.of("stable-content", "stable-first-line\nadded-by-explicit-merge\n"),
                implicitMergeChange,
                stableBranchTip)
            .getChangeId();
    gApi.changes().id(implicitMergeChangeId).current().review(ReviewInput.approve());
    gApi.changes().id(explicitMergeChangeId).current().review(ReviewInput.approve());
  }

  @Test
  public void doesntAddContentFromParentForImplicitMergeChange() throws Exception {
    gApi.changes().id(implicitMergeChangeId).current().submit();

    ChangeInfo ci = gApi.changes().id(implicitMergeChangeId).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());

    assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
        .containsExactly(
            "master-content2", "added-by-implicit-merge\n",
            "stable-content", "stable-first-line\n");
  }

  @Test
  public void canSubmitImplicitMergeChange() throws Exception {
    gApi.changes().id(implicitMergeChangeId).current().submit();

    ChangeInfo ci = gApi.changes().id(implicitMergeChangeId).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());
  }
}
