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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.util.CommitMessageUtil.generateChangeId;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.submit.IntegrationConflictException;
import com.google.gerrit.testing.ConfigSuite;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Creates 2 changes for tests.
 *
 * <p>Returns arrays of the size 2 with changes ids.
 *
 * The changes forms the following tree:
 * <pre>
 * change[1] (target - stable, merges stable branch and change[0])
 * |         \
 * |         change[0] (target - stable)
 * |          |
 * stable     master
 * </pre>
 *
 * <p>Here change[1] is an expicit merge and can be submitted to stable. The change[0] if
 * submitted alone implicitly merges master into a stable branch.
 */
public class ImplicitMergeOnSubmitCheckIT extends AbstractDaemonTest {
  @Override
  protected boolean enableExperimentsRejectImplicitMergesOnMerge() {
    // Tests uses own experiment setup.
    return false;
  }

  @ConfigSuite.Configs
  public static ImmutableMap<String, Config> configs() {
    ImmutableMap.Builder<String, Config> builder = ImmutableMap.builder();
    for(SubmitType submitType: SubmitType.values()) {
      if(submitType == SubmitType.INHERIT) {
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
    // The ConfigSuite runner always adds a default config. Ignore it (submitType is not set for it).
    assume().that(cfg.getString("test", null, "submitType")).isNotEmpty();
    RevCommit base = repo().parseCommit(repo().exactRef("HEAD").getObjectId());
//    RevCommit masterBranchTip = pushTo("refs/heads/master", ImmutableMap.of("master-content", "master-first-line\n"), base).getCommit();
//    RevCommit stableBranchTip = pushTo("refs/heads/stable", ImmutableMap.of("stable-content", "stable-first-line\n"), base).getCommit();
    RevCommit stableBranchTip = pushTo("refs/heads/stable",
        ImmutableMap.of("stable-content", "stable-first-line\n"), base).getCommit();
    RevCommit masterBranchTip = pushTo("refs/heads/master",
        ImmutableMap.of("master-content", "master-first-line\n"), stableBranchTip).getCommit();
    implicitMergeChangeId = "I" + generateChangeId().name();
    RevCommit implicitMergeChange = createChangeWithoutPush(implicitMergeChangeId,
        ImmutableMap.of("master-content2", "added-by-implicit-merge\n"), masterBranchTip);
    explicitMergeChangeId = pushTo("refs/for/stable",
        ImmutableMap.of("stable-content", "stable-first-line\nadded-by-explicit-merge\n"),
        implicitMergeChange, stableBranchTip).getChangeId();
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
  public void alwaysRejectOnMerge_rejectImplicitMergeFalse_rejectImplicitMergeOnSubmit() throws Exception {
    setRejectImplicitMerges(/*reject=*/false);
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
  public void alwaysRejectOnMerge_rejectImplicitMergeFalse_canSubmitExplicitMerge() throws Exception {
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/false);
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
  public void alwaysRejectOnMerge_rejectImplicitMergeTrue_rejectImplicitMergeOnSubmit() throws Exception {
    setRejectImplicitMerges(/*reject=*/true);
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
  public void alwaysRejectOnMerge_rejectImplicitMergeTrue_canSubmitExplicitMerge() throws Exception {
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/true);
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
    setRejectImplicitMerges(/*reject=*/false);
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
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/false);
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
    setRejectImplicitMerges(/*reject=*/true);
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
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/true);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
          "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeFalse_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/*reject=*/false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
          "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeFalse_canSubmitExplicitMerge() throws Exception {
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/false);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
          "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeTrue_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/*reject=*/true);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
          "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_rejectImplicitMergeTrue_canSubmitExplicitMerge() throws Exception {
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/true);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeFalse_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/*reject=*/false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeFalse_canSubmitExplicitMerge() throws Exception {
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/false);
    assertThatExcplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeTrue_canSubmitImplicitMerge() throws Exception {
    setRejectImplicitMerges(/*reject=*/false);
    assertThatImplicitMergeSubmitAllowed();
  }

  @Test
  public void noExperiments_rejectImplicitMergeTrue_canSubmitExplicitMerge() throws Exception {
    // The CherryPick strategy always pick a single change. It can't merge the explicit merge change
    // if the parent is not submitting - i.e. this test always fail with the cherry pick strategy,
    // so skip it.
    assume().that(submitType).isNotEqualTo(SubmitType.CHERRY_PICK);
    setRejectImplicitMerges(/*reject=*/true);
    assertThatExcplicitMergeSubmitAllowed();
  }

  private void assertThatImplicitMergeSubmitRejected() throws Exception {
    IntegrationConflictException e =
        assertThrows(
            IntegrationConflictException.class,
            () -> gApi.changes().id(implicitMergeChangeId).current().submit());
    assertThat(e.getMessage().toLowerCase()).contains("implicit merge detected");
    ChangeInfo ci = gApi.changes().id(implicitMergeChangeId).info();
    assertThat(ci.submitted).isNull();
    assertThat(getRemoteBranchRootPathContent("refs/heads/stable")).containsExactly(
        "stable-content", "stable-first-line\n"
    );
  }

  private void assertThatImplicitMergeSubmitAllowed() throws Exception {
    gApi.changes().id(implicitMergeChangeId).current().submit();

    ChangeInfo ci = gApi.changes().id(implicitMergeChangeId).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());

    if(submitType != SubmitType.REBASE_ALWAYS && submitType != SubmitType.CHERRY_PICK) {
      assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
          .containsExactly(
              "master-content", "master-first-line\n",
              "master-content2", "added-by-implicit-merge\n",
              "stable-content", "stable-first-line\n"
          );
    } else {
      assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
          .containsExactly(
              "master-content2", "added-by-implicit-merge\n",
              "stable-content", "stable-first-line\n"
          );
    }
  }

  private void assertThatExcplicitMergeSubmitAllowed() throws Exception {
    gApi.changes().id(explicitMergeChangeId).current().submit();

    ChangeInfo ci = gApi.changes().id(explicitMergeChangeId).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());
    assertThat(getRemoteBranchRootPathContent("refs/heads/stable"))
        .containsExactly(
            "master-content", "master-first-line\n",
            "master-content2", "added-by-implicit-merge\n",
            "stable-content", "stable-first-line\nadded-by-explicit-merge\n"
        );
  }

  PushOneCommit.Result pushTo(String ref, ImmutableMap<String, String> files, RevCommit... parents) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Some commit", files);
    push.setParents(List.of(parents));
    PushOneCommit.Result result = push.to(ref);
    result.assertOkStatus();
    return result;
  }

  RevCommit createChangeWithoutPush(String changeId, ImmutableMap<String, String> files, RevCommit... parents) throws Exception {
    TestRepository.CommitBuilder commitBuilder = testRepo.commit()
        .message("Change " + changeId)
        // The passed changeId starts with 'I', but insertChangeId expects id without 'I'.
        .insertChangeId(changeId.substring(1));
    for(RevCommit parent: parents) {
      commitBuilder.parent(parent);
    }
    for(Map.Entry<String, String> entry: files.entrySet()) {
      commitBuilder.add(entry.getKey(), entry.getValue());
    }

    return commitBuilder.create();
  }
  private void setRejectImplicitMerges(boolean reject) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              p ->
                  p.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES,
                      reject ? InheritableBoolean.TRUE : InheritableBoolean.FALSE));
      u.save();
    }
  }

  private void setSubmitType(SubmitType submitType) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              p ->
                  p.setSubmitType(submitType));
      u.save();
    }
  }

  ImmutableMap<String, String> getRemoteBranchRootPathContent(String refName) throws Exception {
    String revision = gApi.projects().name(project.get()).branch(refName).get().revision;
    testRepo.git().fetch().setRemote("origin").call();
    RevTree revTree = testRepo.getRepository().parseCommit(testRepo.getRepository().resolve(revision)).getTree();
    try(TreeWalk tw = new TreeWalk(testRepo.getRepository())) {
      tw.setFilter(TreeFilter.ALL);
      tw.setRecursive(false);
      tw.reset(revTree);
      tw.setRecursive(false);
      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      while (tw.next()) {
        String path = tw.getPathString();
        String content = RawParseUtils.decode(
            testRepo.getRepository().open(tw.getObjectId(0)).getCachedBytes(Integer.MAX_VALUE));
        builder.put(path, content);

      }
      return builder.buildOrThrow();
    }
  }


}
