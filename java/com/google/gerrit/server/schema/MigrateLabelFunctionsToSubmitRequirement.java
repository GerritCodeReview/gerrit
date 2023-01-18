// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectLevelConfig;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A class with logic for migrating existing label functions to submit requirements and resetting
 * the label functions to {@link LabelFunction#NO_BLOCK}.
 *
 * <p>Important note: Callers should do this migration only if this gerrit installation has no
 * Prolog submit rules (i.e. no rules.pl file in refs/meta/config). Otherwise, the newly created
 * submit requirements might not behave as intended.
 *
 * <p>The conversion is done as follows:
 *
 * <ul>
 *   <li>MaxWithBlock is translated to submittableIf = label:$lbl=MAX AND -label:$lbl=MIN
 *   <li>MaxNoBlock is translated to submittableIf = label:$lbl=MAX
 *   <li>AnyWithBlock is translated to submittableIf = -label:$lbl=MIN
 *   <li>NoBlock/NoOp are translated to applicableIf = is:false (not applicable)
 *   <li>PatchSetLock labels are left as is
 * </ul>
 *
 * If the label has {@link LabelType#isIgnoreSelfApproval()}, the max vote is appended with the
 * 'user=non_uploader' argument.
 *
 * <p>For labels that were skipped, i.e. had only one "zero" predefined value, the migrator creates
 * a non-applicable submit-requirement for them. This is done so that if a parent project had a
 * submit-requirement with the same name, then it's not inherited by this project.
 *
 * <p>If there is an existing label and there exists a "submit requirement" with the same name, the
 * migrator checks if the submit-requirement to be created matches the one in project.config. If
 * they don't match, a warning message is printed, otherwise nothing happens. In either cases, the
 * existing submit-requirement is not altered.
 */
public class MigrateLabelFunctionsToSubmitRequirement {
  public static final String COMMIT_MSG = "Migrate label functions to submit requirements";
  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;

  public enum Status {
    /**
     * The migrator updated the project config and created new submit requirements and/or did reset
     * label functions.
     */
    MIGRATED,

    /** The project had prolog rules, and the migration was skipped. */
    HAS_PROLOG,

    /**
     * The project was migrated with a previous run of this class. The migration for this run was
     * skipped.
     */
    PREVIOUSLY_MIGRATED,

    /**
     * Migration was run for the project but did not update the project.config because it was
     * up-to-date.
     */
    NO_CHANGE
  }

  @Inject
  public MigrateLabelFunctionsToSubmitRequirement(
      GitRepositoryManager repoManager, @GerritPersonIdent PersonIdent serverUser) {
    this.repoManager = repoManager;
    this.serverUser = serverUser;
  }

  /**
   * For each label function, create a corresponding submit-requirement and set the label function
   * to NO_BLOCK. Blocking label functions are substituted with blocking submit-requirements.
   * Non-blocking label functions are substituted with non-applicable submit requirements, allowing
   * the label vote to be surfaced as a trigger vote (optional label).
   *
   * @return {@link Status} reflecting the status of the migration.
   */
  public Status executeMigration(Project.NameKey project, UpdateUI ui)
      throws IOException, ConfigInvalidException {
    if (hasPrologRules(project)) {
      ui.message(String.format("Skipping project %s because it has prolog rules", project));
      return Status.HAS_PROLOG;
    }
    ProjectLevelConfig.Bare projectConfig =
        new ProjectLevelConfig.Bare(ProjectConfig.PROJECT_CONFIG);
    boolean migrationPerformed = false;
    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, project, repo)) {
      if (hasMigrationAlreadyRun(repo)) {
        ui.message(
            String.format(
                "Skipping migrating label functions to submit requirements for project '%s'"
                    + " because it has been previously migrated",
                project));
        return Status.PREVIOUSLY_MIGRATED;
      }
      projectConfig.load(project, repo);
      Config cfg = projectConfig.getConfig();
      Map<String, LabelAttributes> labelTypes = getLabelTypes(cfg);
      Map<String, SubmitRequirement> existingSubmitRequirements = loadSubmitRequirements(cfg);
      boolean updated = false;
      for (Map.Entry<String, LabelAttributes> lt : labelTypes.entrySet()) {
        String labelName = lt.getKey();
        LabelAttributes attributes = lt.getValue();
        if (attributes.function().equals("PatchSetLock")) {
          // PATCH_SET_LOCK functions should be left as is
          continue;
        }
        // If the function is other than "NoBlock" we want to reset the label function regardless
        // of whether there exists a "submit requirement".
        if (!attributes.function().equals("NoBlock")) {
          updated = true;
          writeLabelFunction(cfg, labelName, "NoBlock");
        }
        Optional<SubmitRequirement> sr = createSrFromLabelDef(labelName, attributes);
        if (!sr.isPresent()) {
          continue;
        }
        // Make the operation idempotent by skipping creating the submit-requirement if one was
        // already created or previously existed.
        if (existingSubmitRequirements.containsKey(labelName.toLowerCase(Locale.ROOT))) {
          if (!sr.get()
              .equals(existingSubmitRequirements.get(labelName.toLowerCase(Locale.ROOT)))) {
            ui.message(
                String.format(
                    "Warning: Skipping creating a submit requirement for label '%s'. An existing "
                        + "submit requirement is already present but its definition is not "
                        + "identical to the existing label definition.",
                    labelName));
          }
          continue;
        }
        updated = true;
        ui.message(
            String.format(
                "Project %s: Creating a submit requirement for label %s", project, labelName));
        writeSubmitRequirement(cfg, sr.get());
      }
      if (updated) {
        commit(projectConfig, md);
        migrationPerformed = true;
      }
    }
    return migrationPerformed ? Status.MIGRATED : Status.NO_CHANGE;
  }

  /**
   * Returns a Map containing label names as string in keys along with some of its attributes (that
   * we need in the migration) like canOverride, ignoreSelfApproval and function in the value.
   */
  private Map<String, LabelAttributes> getLabelTypes(Config cfg) {
    Map<String, LabelAttributes> labelTypes = new HashMap<>();
    for (String labelName : cfg.getSubsections(ProjectConfig.LABEL)) {
      String function = cfg.getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_FUNCTION);
      boolean canOverride =
          cfg.getBoolean(
              ProjectConfig.LABEL,
              labelName,
              ProjectConfig.KEY_CAN_OVERRIDE,
              /* defaultValue= */ true);
      boolean ignoreSelfApproval =
          cfg.getBoolean(
              ProjectConfig.LABEL,
              labelName,
              ProjectConfig.KEY_IGNORE_SELF_APPROVAL,
              /* defaultValue= */ false);
      ImmutableList<String> values =
          ImmutableList.<String>builder()
              .addAll(
                  Arrays.asList(
                      cfg.getStringList(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_VALUE)))
              .build();
      ImmutableList<String> refPatterns =
          ImmutableList.<String>builder()
              .addAll(
                  Arrays.asList(
                      cfg.getStringList(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_BRANCH)))
              .build();
      LabelAttributes attributes =
          LabelAttributes.create(
              function == null ? "MaxWithBlock" : function,
              canOverride,
              ignoreSelfApproval,
              values,
              refPatterns);
      labelTypes.put(labelName, attributes);
    }
    return labelTypes;
  }

  private void writeSubmitRequirement(Config cfg, SubmitRequirement sr) {
    if (sr.description().isPresent()) {
      cfg.setString(
          ProjectConfig.SUBMIT_REQUIREMENT,
          sr.name(),
          ProjectConfig.KEY_SR_DESCRIPTION,
          sr.description().get());
    }
    if (sr.applicabilityExpression().isPresent()) {
      cfg.setString(
          ProjectConfig.SUBMIT_REQUIREMENT,
          sr.name(),
          ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION,
          sr.applicabilityExpression().get().expressionString());
    }
    cfg.setString(
        ProjectConfig.SUBMIT_REQUIREMENT,
        sr.name(),
        ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
        sr.submittabilityExpression().expressionString());
    if (sr.overrideExpression().isPresent()) {
      cfg.setString(
          ProjectConfig.SUBMIT_REQUIREMENT,
          sr.name(),
          ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
          sr.overrideExpression().get().expressionString());
    }
    cfg.setBoolean(
        ProjectConfig.SUBMIT_REQUIREMENT,
        sr.name(),
        ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
        sr.allowOverrideInChildProjects());
  }

  private void writeLabelFunction(Config cfg, String labelName, String function) {
    cfg.setString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_FUNCTION, function);
  }

  private void commit(ProjectLevelConfig.Bare projectConfig, MetaDataUpdate md) throws IOException {
    md.getCommitBuilder().setAuthor(serverUser);
    md.getCommitBuilder().setCommitter(serverUser);
    md.setMessage(COMMIT_MSG);
    projectConfig.commit(md);
  }

  private static Optional<SubmitRequirement> createSrFromLabelDef(
      String labelName, LabelAttributes attributes) {
    if (isLabelSkipped(attributes.values())) {
      return Optional.of(createNonApplicableSr(labelName, attributes.canOverride()));
    } else if (isBlockingOrRequiredLabel(attributes.function())) {
      return Optional.of(createBlockingOrRequiredSr(labelName, attributes));
    }
    return Optional.empty();
  }

  private static SubmitRequirement createNonApplicableSr(String labelName, boolean canOverride) {
    return SubmitRequirement.builder()
        .setName(labelName)
        .setApplicabilityExpression(SubmitRequirementExpression.of("is:false"))
        .setSubmittabilityExpression(SubmitRequirementExpression.create("is:true"))
        .setAllowOverrideInChildProjects(canOverride)
        .build();
  }

  /**
   * Create a "submit requirement" that is only satisfied if the label is voted with the max votes
   * and/or not voted by the min vote, according to the label attributes.
   */
  private static SubmitRequirement createBlockingOrRequiredSr(
      String labelName, LabelAttributes attributes) {
    SubmitRequirement.Builder builder =
        SubmitRequirement.builder()
            .setName(labelName)
            .setAllowOverrideInChildProjects(attributes.canOverride());
    String maxPart =
        String.format("label:%s=MAX", labelName)
            + (attributes.ignoreSelfApproval() ? ",user=non_uploader" : "");
    switch (attributes.function()) {
      case "MaxWithBlock":
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(
                String.format("%s AND -label:%s=MIN", maxPart, labelName)));
        break;
      case "AnyWithBlock":
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(String.format("-label:%s=MIN", labelName)));
        break;
      case "MaxNoBlock":
        builder.setSubmittabilityExpression(SubmitRequirementExpression.create(maxPart));
        break;
      default:
        break;
    }
    if (!attributes.refPatterns().isEmpty()) {
      builder.setApplicabilityExpression(
          SubmitRequirementExpression.of(
              String.join(
                  " OR ",
                  attributes.refPatterns().stream()
                      .map(b -> "branch:" + b)
                      .collect(Collectors.toList()))));
    }
    return builder.build();
  }

  private static boolean isBlockingOrRequiredLabel(String function) {
    return function.equals("AnyWithBlock")
        || function.equals("MaxWithBlock")
        || function.equals("MaxNoBlock");
  }

  /**
   * Returns true if the label definition was skipped in the project, i.e. it had only one defined
   * value: zero.
   */
  private static boolean isLabelSkipped(List<String> values) {
    return values.isEmpty() || (values.size() == 1 && values.get(0).startsWith("0"));
  }

  public boolean anyProjectHasProlog(Collection<Project.NameKey> allProjects) throws IOException {
    for (Project.NameKey p : allProjects) {
      if (hasPrologRules(p)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasPrologRules(Project.NameKey project) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo);
        ObjectReader reader = rw.getObjectReader()) {
      Ref refsConfig = repo.exactRef(RefNames.REFS_CONFIG);
      if (refsConfig == null) {
        // Project does not have a refs/meta/config and no rules.pl consequently.
        return false;
      }
      RevCommit commit = repo.parseCommit(refsConfig.getObjectId());
      try (TreeWalk tw = TreeWalk.forPath(reader, "rules.pl", commit.getTree())) {
        if (tw != null) {
          return true;
        }
      }

      return false;
    }
  }

  /**
   * Returns a map containing submit requirement names in lower name as keys, with {@link
   * com.google.gerrit.entities.SubmitRequirement} as value.
   */
  private Map<String, SubmitRequirement> loadSubmitRequirements(Config rc) {
    Map<String, SubmitRequirement> allRequirements = new LinkedHashMap<>();
    for (String name : rc.getSubsections(ProjectConfig.SUBMIT_REQUIREMENT)) {
      String description =
          rc.getString(ProjectConfig.SUBMIT_REQUIREMENT, name, ProjectConfig.KEY_SR_DESCRIPTION);
      String applicabilityExpr =
          rc.getString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              name,
              ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION);
      String submittabilityExpr =
          rc.getString(
              ProjectConfig.SUBMIT_REQUIREMENT,
              name,
              ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION);
      String overrideExpr =
          rc.getString(
              ProjectConfig.SUBMIT_REQUIREMENT, name, ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION);
      boolean canInherit =
          rc.getBoolean(
              ProjectConfig.SUBMIT_REQUIREMENT,
              name,
              ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
              false);
      SubmitRequirement submitRequirement =
          SubmitRequirement.builder()
              .setName(name)
              .setDescription(Optional.ofNullable(description))
              .setApplicabilityExpression(SubmitRequirementExpression.of(applicabilityExpr))
              .setSubmittabilityExpression(SubmitRequirementExpression.create(submittabilityExpr))
              .setOverrideExpression(SubmitRequirementExpression.of(overrideExpr))
              .setAllowOverrideInChildProjects(canInherit)
              .build();
      allRequirements.put(name.toLowerCase(Locale.ROOT), submitRequirement);
    }
    return allRequirements;
  }

  private static boolean hasMigrationAlreadyRun(Repository repo) throws IOException {
    try (RevWalk revWalk = new RevWalk(repo)) {
      Ref refsMetaConfig = repo.exactRef(RefNames.REFS_CONFIG);
      if (refsMetaConfig == null) {
        return false;
      }
      revWalk.markStart(revWalk.parseCommit(refsMetaConfig.getObjectId()));
      RevCommit commit;
      while ((commit = revWalk.next()) != null) {
        if (COMMIT_MSG.equals(commit.getShortMessage())) {
          return true;
        }
      }
      return false;
    }
  }

  @AutoValue
  abstract static class LabelAttributes {
    abstract String function();

    abstract boolean canOverride();

    abstract boolean ignoreSelfApproval();

    abstract ImmutableList<String> values();

    abstract ImmutableList<String> refPatterns();

    static LabelAttributes create(
        String function,
        boolean canOverride,
        boolean ignoreSelfApproval,
        ImmutableList<String> values,
        ImmutableList<String> refPatterns) {
      return new AutoValue_MigrateLabelFunctionsToSubmitRequirement_LabelAttributes(
          function, canOverride, ignoreSelfApproval, values, refPatterns);
    }
  }
}
