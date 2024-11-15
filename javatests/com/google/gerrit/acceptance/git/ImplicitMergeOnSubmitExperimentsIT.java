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
import com.google.gerrit.acceptance.config.GerritConfig;
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
 * Verifies that gerrit correctly rejects or submits implicit merges depending on experiments.
 *
 * <p>All tests use the same commit configuration (master branch is one commit ahead of stable
 * branch):
 *
 * <pre>{@code
 * change[1] (target - stable, explicit merge of stable branch and master branches)
 * |         \
 * |         change[0] (target - stable, i.e. implicit merge of master and stable branches)
 * |          |
 * |        master
 * |           |
 * stable <--- |
 * }</pre>
 */
public class ImplicitMergeOnSubmitExperimentsIT extends AbstractImplicitMergeTest {
  @Override
  protected boolean enableExperimentsRejectImplicitMergesOnMerge() {
    // Tests uses own experiment setup.
    return false;
  }

  @ConfigSuite.Configs
  public static ImmutableMap<String, Config> configs() {
    // The @RunWith(Parameterized.class) can't be used, because AbstractDaemonClass already
    // uses @RunWith(ConfigSuite.class). Emulate parameters using configs.
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

  private SubmitType submitType;

  @Before
  public void setUp() throws Exception {
    // The ConfigSuite runner always adds a default config. Ignore it (submitType is not set for
    // it).
    assume().that(cfg.getString("test", null, "submitType")).isNotEmpty();
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
    submitType = SubmitType.valueOf(cfg.getString("test", null, "submitType"));
    setSubmitType(submitType);
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
        "GerritBackendFeature__always_reject_implicit_merges_on_merge"
      })
  public void alwaysRejectOnMerge_rejectImplicitMergeFalse_rejectImplicitMergeOnSubmit()
      throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatImplicitMergeSubmitRejected();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
        "GerritBackendFeature__always_reject_implicit_merges_on_merge"
      })
  public void alwaysRejectOnMerge_rejectImplicitMergeFalse_canSubmitExplicitMerge()
      throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
        "GerritBackendFeature__always_reject_implicit_merges_on_merge"
      })
  public void alwaysRejectOnMerge_rejectImplicitMergeTrue_rejectImplicitMergeOnSubmit()
      throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatImplicitMergeSubmitRejected();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
        "GerritBackendFeature__always_reject_implicit_merges_on_merge"
      })
  public void alwaysRejectOnMerge_rejectImplicitMergeTrue_canSubmitExplicitMerge()
      throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
      })
  public void rejectOnMerge_rejectImplicitMergeFalse_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
      })
  public void rejectOnMerge_rejectImplicitMergeFalse_canSubmitExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
      })
  public void rejectOnMerge_rejectImplicitMergeTrue_rejectImplicitMergeOnSubmit() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatImplicitMergeSubmitRejected();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
      })
  public void rejectOnMerge_rejectImplicitMergeTrue_canSubmitExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeFalse_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeFalse_canSubmitExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeTrue_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeTrue_canSubmitExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeFalse_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeFalse_canSubmitExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ false);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeTrue_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeTrue_canSubmitExplicitMerge() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
    assertThatExcplicitMergeSubmitAllowed();
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

    if (submitType != SubmitType.REBASE_ALWAYS) {
      assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
          .containsExactly(
              "master-content", "master-first-line\n",
              "master-content2", "added-by-implicit-merge\n",
              "stable-content", "stable-first-line\n");
    } else {
      assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
          .containsExactly(
              "master-content2", "added-by-implicit-merge\n",
              "stable-content", "stable-first-line\n");
    }
  }

  private void assertThatExcplicitMergeSubmitAllowed() throws Exception {
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
            "stable-content", "stable-first-line\nadded-by-explicit-merge\n");
  }
}
