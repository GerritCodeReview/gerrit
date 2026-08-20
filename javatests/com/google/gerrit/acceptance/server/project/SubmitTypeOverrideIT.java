// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.SubmitType.CHERRY_PICK;
import static com.google.gerrit.extensions.client.SubmitType.FAST_FORWARD_ONLY;
import static com.google.gerrit.extensions.client.SubmitType.MERGE_ALWAYS;
import static com.google.gerrit.extensions.client.SubmitType.MERGE_IF_NECESSARY;
import static com.google.gerrit.extensions.client.SubmitType.REBASE_IF_NECESSARY;
import static com.google.gerrit.server.project.ProjectConfig.RULES_PL_FILE;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.SubmitTypeOverride;
import com.google.gerrit.entities.SubmitTypeOverrideExpression;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.project.ProjectConfig;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance tests for submit type overrides ({@code [submit-type "<name>"] applicableIf = ...}).
 *
 * <p>These tests verify the full evaluation stack: config parsing → {@code SubmitTypeEvaluator} →
 * the REST API endpoint that exposes the resolved submit type.
 */
@NoHttpd
public class SubmitTypeOverrideIT extends AbstractDaemonTest {

  private AtomicInteger fileCounter;

  @Before
  public void setUp() {
    fileCounter = new AtomicInteger();
  }

  @Test
  public void noOverride_usesProjectDefault() throws Exception {
    updateProjectConfig(cfg -> cfg.updateProject(p -> p.setSubmitType(FAST_FORWARD_ONLY)));

    PushOneCommit.Result r = createChangeOnBranch("master");
    assertSubmitType(FAST_FORWARD_ONLY, r.getChangeId());
  }

  @Test
  public void override_appliesToMatchingBranch() throws Exception {
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(REBASE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(MERGE_ALWAYS)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("branch:stable"))
                  .build());
        });

    PushOneCommit.Result firstChange = createChangeOnBranch("master");
    RevCommit branchPoint = firstChange.getCommit().getParent(0);
    gApi.projects()
        .name(project.get())
        .branch("stable")
        .create(
            new BranchInput() {
              {
                revision = branchPoint.name();
              }
            });

    PushOneCommit.Result onMaster = createChangeOnBranch("master");
    testRepo.reset(branchPoint);
    PushOneCommit.Result onStable = createChangeOnBranch("stable");

    assertSubmitType(REBASE_IF_NECESSARY, onMaster.getChangeId());
    assertSubmitType(MERGE_ALWAYS, onStable.getChangeId());
  }

  @Test
  public void multipleOverrides_appliesToMatchingBranches() throws Exception {
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(CHERRY_PICK)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("branch:cherry"))
                  .build());
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(FAST_FORWARD_ONLY)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("branch:ff"))
                  .build());
        });

    PushOneCommit.Result seed = createChangeOnBranch("master");
    RevCommit branchPoint = seed.getCommit().getParent(0);
    gApi.projects()
        .name(project.get())
        .branch("cherry")
        .create(
            new BranchInput() {
              {
                revision = branchPoint.name();
              }
            });
    gApi.projects()
        .name(project.get())
        .branch("ff")
        .create(
            new BranchInput() {
              {
                revision = branchPoint.name();
              }
            });

    PushOneCommit.Result rMaster = createChangeOnBranch("master");

    testRepo.reset(branchPoint);
    PushOneCommit.Result rCherry = createChangeOnBranch("cherry");

    testRepo.reset(branchPoint);
    PushOneCommit.Result rFf = createChangeOnBranch("ff");

    assertSubmitType(MERGE_IF_NECESSARY, rMaster.getChangeId());
    assertSubmitType(CHERRY_PICK, rCherry.getChangeId());
    assertSubmitType(FAST_FORWARD_ONLY, rFf.getChangeId());
  }

  @Test
  public void removingOverride_fallsBackToProjectDefault() throws Exception {
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(MERGE_ALWAYS)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("branch:stable"))
                  .build());
        });

    PushOneCommit.Result seed = createChangeOnBranch("master");
    RevCommit branchPoint = seed.getCommit().getParent(0);
    gApi.projects()
        .name(project.get())
        .branch("stable")
        .create(
            new BranchInput() {
              {
                revision = branchPoint.name();
              }
            });
    testRepo.reset(branchPoint);
    PushOneCommit.Result r = createChangeOnBranch("stable");

    // Override is active: stable branch should use MERGE_ALWAYS.
    assertSubmitType(MERGE_ALWAYS, r.getChangeId());

    // Remove the override — only keep the project-wide default.
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.getSubmitTypeSections().clear();
        });

    // The same change should now resolve to the project-wide default.
    assertSubmitType(MERGE_IF_NECESSARY, r.getChangeId());
  }

  @Test
  public void upsertOverride_changesApplicableType() throws Exception {
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(MERGE_ALWAYS)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("branch:stable"))
                  .build());
        });

    PushOneCommit.Result seed = createChangeOnBranch("master");
    RevCommit branchPoint = seed.getCommit().getParent(0);
    gApi.projects()
        .name(project.get())
        .branch("stable")
        .create(
            new BranchInput() {
              {
                revision = branchPoint.name();
              }
            });
    testRepo.reset(branchPoint);
    PushOneCommit.Result r = createChangeOnBranch("stable");
    assertSubmitType(MERGE_ALWAYS, r.getChangeId());

    // Replace the override for "stable" with cherry pick.
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.getSubmitTypeSections().clear();
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(CHERRY_PICK)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("branch:stable"))
                  .build());
        });

    assertSubmitType(CHERRY_PICK, r.getChangeId());
  }

  private PushOneCommit.Result createChangeOnBranch(String branch) throws Exception {
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Test change",
            "file" + fileCounter.incrementAndGet() + ".txt",
            "content");
    PushOneCommit.Result r = push.to("refs/for/" + branch);
    r.assertOkStatus();
    return r;
  }

  private void assertSubmitType(SubmitType expected, String changeId) throws Exception {
    assertThat(gApi.changes().id(changeId).current().submitType()).isEqualTo(expected);
  }

  private void updateProjectConfig(Consumer<ProjectConfig> configUpdater) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      md.setMessage("Update project.config for submit type override test");
      ProjectConfig cfg = projectConfigFactory.read(md);
      configUpdater.accept(cfg);
      cfg.commit(md);
    }
    projectCache.evict(project);
  }

  // ---------------------------------------------------------------------------
  // Prolog interaction tests
  // ---------------------------------------------------------------------------

  /**
   * When Prolog rules are globally enabled and a {@code rules.pl} file contains an explicit {@code
   * submit_type} clause that matches the change, Prolog wins over any submit type override.
   *
   * <p>Priority: explicit Prolog rule &gt; submit type override &gt; project default.
   */
  @Test
  @GerritConfig(name = "rules.enable", value = "true")
  public void prologRule_takesOverSubmitTypeOverride() throws Exception {
    // Project default: MERGE_IF_NECESSARY.
    // Override: all changes → FAST_FORWARD_ONLY.
    // Prolog rule: unconditionally returns CHERRY_PICK.
    //
    // Expected: CHERRY_PICK (explicit Prolog clause wins).
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(FAST_FORWARD_ONLY)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("is:open"))
                  .build());
        });
    setRulesPl("submit_type(cherry_pick).");

    PushOneCommit.Result r = createChangeOnBranch("master");
    assertSubmitType(CHERRY_PICK, r.getChangeId());
  }

  /**
   * When Prolog rules are globally enabled but the project has no {@code rules.pl} file, {@code
   * getExplicitSubmitType} returns empty and submit type overrides are evaluated normally.
   */
  @Test
  @GerritConfig(name = "rules.enable", value = "true")
  public void prologEnabled_noRulesPl_submitTypeOverrideIsActive() throws Exception {
    // Project default: REBASE_IF_NECESSARY.
    // Override: all changes → MERGE_ALWAYS.
    // No rules.pl → getExplicitSubmitType returns empty → override is evaluated.
    //
    // Expected: MERGE_ALWAYS (override wins, no Prolog rule present).
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(REBASE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(MERGE_ALWAYS)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("is:open"))
                  .build());
        });
    // No setRulesPl() call — project has no rules.pl.

    PushOneCommit.Result r = createChangeOnBranch("master");
    assertSubmitType(MERGE_ALWAYS, r.getChangeId());
  }

  /**
   * When Prolog rules are globally enabled and the {@code rules.pl} file contains a conditional
   * {@code submit_type/1} clause with no fallback:
   *
   * <ul>
   *   <li>Changes matched by the explicit clause → Prolog's type wins.
   *   <li>Changes NOT matched by any clause → {@code locate_submit_type} fails, {@code
   *       getExplicitSubmitType} returns empty, and submit type overrides are evaluated next.
   * </ul>
   *
   * <p>Note: a {@code submit_type(T) :- gerrit:project_default_submit_type(T).} fallback clause is
   * intentionally omitted from the test's {@code rules.pl}. A user who writes such an explicit
   * delegation clause is expressing "I want the project default here" and Prolog wins for that case
   * as well.
   */
  @Test
  @GerritConfig(name = "rules.enable", value = "true")
  public void prologRule_withProjectDefaultFallback_overrideAppliesWhenPrologFallsThrough()
      throws Exception {
    // Project default: MERGE_IF_NECESSARY.
    // Override: all open changes → CHERRY_PICK.
    // Prolog rule: returns FAST_FORWARD_ONLY for commits containing "USE_FF".
    //   No fallback clause: locate_submit_type fails for non-matching changes,
    //   so the Java evaluator falls through to submit type overrides.
    //
    // With marker   → FAST_FORWARD_ONLY (Prolog explicit clause).
    // Without marker → CHERRY_PICK      (Prolog falls through → override wins).
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(CHERRY_PICK)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("is:open"))
                  .build());
        });
    setRulesPl(
        "submit_type(fast_forward_only) :-"
            + "gerrit:commit_message(M),"
            + "regex_matches('.*USE_FF.*', M),"
            + "!.\n");
    // No project_default_submit_type fallback: when no clause matches, locate_submit_type
    // fails and the Java evaluator falls through to submit type overrides.

    PushOneCommit.Result withMarker =
        pushFactory
            .create(admin.newIdent(), testRepo, "USE_FF", "a.txt", "x")
            .to("refs/for/master");
    withMarker.assertOkStatus();

    PushOneCommit.Result withoutMarker =
        pushFactory
            .create(admin.newIdent(), testRepo, "regular change", "b.txt", "y")
            .to("refs/for/master");
    withoutMarker.assertOkStatus();

    // Explicit Prolog clause matched → Prolog wins.
    assertSubmitType(FAST_FORWARD_ONLY, withMarker.getChangeId());
    // Prolog fell through to project_default_submit_type → override takes effect.
    assertSubmitType(CHERRY_PICK, withoutMarker.getChangeId());
  }

  /**
   * Verifies that disabling Prolog (the default) makes submit type overrides active, i.e. the same
   * project config (override present) produces different results depending on whether the Prolog
   * engine is on or off.
   *
   * <p>This test relies on the default {@code rules.enable = false} — no {@code @GerritConfig}
   * annotation is needed.
   */
  @Test
  public void prologDisabled_submitTypeOverrideIsActive() throws Exception {
    // Project default: MERGE_IF_NECESSARY.
    // Override: all open changes → CHERRY_PICK.
    // Prolog: disabled (default).
    //
    // Expected: CHERRY_PICK (override wins because Prolog is off).
    updateProjectConfig(
        cfg -> {
          cfg.updateProject(p -> p.setSubmitType(MERGE_IF_NECESSARY));
          cfg.upsertSubmitType(
              SubmitTypeOverride.builder()
                  .setType(CHERRY_PICK)
                  .setApplicabilityExpression(SubmitTypeOverrideExpression.of("is:open"))
                  .build());
        });

    PushOneCommit.Result r = createChangeOnBranch("master");
    assertSubmitType(CHERRY_PICK, r.getChangeId());
  }

  // ---------------------------------------------------------------------------
  // Prolog helper
  // ---------------------------------------------------------------------------

  /** Writes a {@code rules.pl} file to the project's {@code refs/meta/config} branch. */
  private void setRulesPl(String rule) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      RulesPl r = new RulesPl();
      r.load(md);
      r.rule = rule;
      r.commit(md);
    }
    projectCache.evict(project);
  }

  private static class RulesPl extends VersionedMetaData {
    private String rule;

    @Override
    protected String getRefName() {
      return com.google.gerrit.entities.RefNames.REFS_CONFIG;
    }

    @Override
    protected void onLoad() throws IOException {
      rule = readUTF8(RULES_PL_FILE);
    }

    @Override
    protected boolean onSave(CommitBuilder commit) throws IOException {
      saveUTF8(RULES_PL_FILE, rule);
      return true;
    }
  }
}
