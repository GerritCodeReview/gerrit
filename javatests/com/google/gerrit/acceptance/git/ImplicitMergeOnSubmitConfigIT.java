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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.util.CommitMessageUtil.generateChangeId;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that receive.rejectImplicitMerges controls implicit merge checks on submit.
 *
 * <p>All tests use the same commit graph, where the change targeted to stable has a parent from
 * master.
 */
public class ImplicitMergeOnSubmitConfigIT extends AbstractImplicitMergeTest {
  @ConfigSuite.Configs
  public static ImmutableMap<String, Config> configs() {
    ImmutableMap.Builder<String, Config> builder = ImmutableMap.builder();
    for (SubmitType submitType : SubmitType.values()) {
      if (submitType == SubmitType.INHERIT
          || submitType == SubmitType.CHERRY_PICK
          || submitType == SubmitType.REBASE_ALWAYS) {
        continue;
      }
      Config cfg = new Config();
      cfg.setString("test", null, "submitType", submitType.name());
      builder.put(String.format("submitType=%s", submitType), cfg);
    }
    return builder.buildOrThrow();
  }

  private String implicitMergeChangeId;
  private String explicitMergeChangeId;

  @Before
  public void setUp() throws Exception {
    String submitTypeValue = cfg.getString("test", null, "submitType");
    assume().that(submitTypeValue).isNotEmpty();
    RevCommit base = repo().parseCommit(repo().exactRef("HEAD").getObjectId());
    RevCommit stableBranchTip =
        pushTo("refs/heads/stable", ImmutableMap.of("stable-content", "stable-first-line\n"), base)
            .getCommit();
    RevCommit masterBranchTip =
        pushTo(
                "refs/heads/master",
                ImmutableMap.of("master-content", "master-first-line\n"),
                stableBranchTip)
            .getCommit();
    implicitMergeChangeId = "I" + generateChangeId().name();
    RevCommit implicitMergeChange =
        createChangeWithoutPush(
            implicitMergeChangeId,
            ImmutableMap.of("master-content2", "added-by-implicit-merge\n"),
            masterBranchTip);
    explicitMergeChangeId =
        pushTo(
                "refs/for/stable",
                ImmutableMap.of("stable-content", "stable-first-line\nadded-by-explicit-merge\n"),
                implicitMergeChange,
                stableBranchTip)
            .getChangeId();
    gApi.changes().id(implicitMergeChangeId).current().review(ReviewInput.approve());
    gApi.changes().id(explicitMergeChangeId).current().review(ReviewInput.approve());
    setSubmitType(SubmitType.valueOf(submitTypeValue));
  }

  @Test
  public void implicitMergeRejectedByDefault() throws Exception {
    assertThatImplicitMergeSubmitRejected();
  }

  @Test
  public void explicitMergeAllowedByDefault() throws Exception {
    assertThatExplicitMergeSubmitAllowed();
  }

  @Test
  public void rejectImplicitMergesFalse_allowsImplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  public void rejectImplicitMergesFalse_allowsExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatExplicitMergeSubmitAllowed();
  }

  private void assertThatImplicitMergeSubmitRejected() throws Exception {
    ResourceConflictException e =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(implicitMergeChangeId).current().submit());
    assertThat(e.getMessage().toLowerCase()).contains("submit makes implicit merge to the branch");
    ChangeInfo ci = gApi.changes().id(implicitMergeChangeId).info();
    assertThat(ci.submitted).isNull();
    assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
        .containsExactly("stable-content", "stable-first-line\n");
  }

  private void assertThatImplicitMergeSubmitAllowed() throws Exception {
    gApi.changes().id(implicitMergeChangeId).current().submit();

    ChangeInfo ci = gApi.changes().id(implicitMergeChangeId).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId)
        .isEqualTo(localCtx.getContext().getUser().getAccountId().get());

    assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
        .containsExactly(
            "master-content", "master-first-line\n",
            "master-content2", "added-by-implicit-merge\n",
            "stable-content", "stable-first-line\n");
  }

  private void assertThatExplicitMergeSubmitAllowed() throws Exception {
    gApi.changes().id(explicitMergeChangeId).current().submit();

    ChangeInfo ci = gApi.changes().id(explicitMergeChangeId).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId)
        .isEqualTo(localCtx.getContext().getUser().getAccountId().get());
    assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
        .containsExactly(
            "master-content", "master-first-line\n",
            "master-content2", "added-by-implicit-merge\n",
            "stable-content", "stable-first-line\n",
            "stable-content", "stable-first-line\nadded-by-explicit-merge\n");
  }
}
