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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests how implicit merges are submitted by the cherry pick and rebase always strategies.
 *
 * <p>Verifies that implicit merges can be submitted and that they don't add content from the
 * implicitly merged branch to the target branch.
 */
public class ImplicitMergeOnSubmitByCherryPickOrRebaseAlwaysIT extends AbstractImplicitMergeTest {
  @ConfigSuite.Configs
  public static ImmutableMap<String, Config> configs() {
    // The @RunWith(Parameterized.class) can't be used, because AbstractDaemonClass already
    // uses @RunWith(ConfigSuite.class). Emulate parameters using configs.
    ImmutableMap.Builder<String, Config> builder = ImmutableMap.builder();
    for (SubmitType submitType : SubmitType.values()) {
      if (submitType != SubmitType.CHERRY_PICK && submitType != SubmitType.REBASE_ALWAYS) {
        continue;
      }
      Config cfg = new Config();
      cfg.setString("test", null, "submitType", submitType.name());
      builder.put(String.format("submitType=%s", submitType), cfg);
    }
    return builder.buildOrThrow();
  }

  private String implicitMergeChangeId;

  @Before
  public void setUp() throws Exception {
    // The ConfigSuite runner always adds a default config. Ignore it (submitType is not set for
    // it).
    String submitType = cfg.getString("test", null, "submitType");
    assume().that(submitType).isNotEmpty();
    setSubmitType(SubmitType.valueOf(submitType));

    setRejectImplicitMerges(false);
    RevCommit base = repo().parseCommit(repo().exactRef("HEAD").getObjectId());
    RevCommit masterBranchTip =
        pushTo("refs/heads/master", ImmutableMap.of("master-content", "master-first-line\n"), base)
            .getCommit();
    pushTo("refs/heads/stable", ImmutableMap.of("stable-content", "stable-first-line\n"), base);
    implicitMergeChangeId =
        pushTo(
                "refs/for/stable",
                ImmutableMap.of("master-content2", "added-by-implicit-merge\n"),
                masterBranchTip)
            .getChangeId();
    gApi.changes().id(implicitMergeChangeId).current().review(ReviewInput.approve());
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.REBASE_MERGE_COMMITS)
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
}
