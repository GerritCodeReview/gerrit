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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.server.project.ProjectConfig.RULES_PL_FILE;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.restapi.project.RepoMetaDataUpdater.ConfigUpdater;
import com.google.gerrit.server.schema.UpdateUI;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectReader;
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

  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private final GitRepositoryManager repoManager;

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
      RepoMetaDataUpdater repoMetaDataUpdater, GitRepositoryManager repoManager) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
    this.repoManager = repoManager;
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
      throws IOException,
          ConfigInvalidException,
          MethodNotAllowedException,
          PermissionBackendException {
    try (ConfigUpdater updater =
        repoMetaDataUpdater.configUpdaterWithoutPermissionsCheck(project, null, COMMIT_MSG)) {
      Status result = updateConfig(project, updater.getConfig(), ui);
      if (result == Status.MIGRATED) {
        updater.commitConfigUpdate();
      }
      return result;
    }
  }

  public Status updateConfig(Project.NameKey project, ProjectConfig projectConfig, UpdateUI ui)
      throws IOException {
    boolean updated = false;
    if (hasPrologRules(project)) {
      ui.message(String.format("Skipping project %s because it has prolog rules", project));
      return Status.HAS_PROLOG;
    }

    if (hasMigrationAlreadyRun(project)) {
      ui.message(
          String.format(
              "Skipping migrating label functions to submit requirements for project '%s'"
                  + " because it has been previously migrated",
              project));
      return Status.PREVIOUSLY_MIGRATED;
    }

    Map<String, LabelType> labelSections = projectConfig.getLabelSections();
    SubmitRequirementMap existingSubmitRequirements =
        new SubmitRequirementMap(projectConfig.getSubmitRequirementSections());

    for (Map.Entry<String, LabelType> section : labelSections.entrySet()) {
      String labelName = section.getKey();
      LabelType labelType = section.getValue();

      if (labelType.getFunction() == LabelFunction.PATCH_SET_LOCK) {
        // PATCH_SET_LOCK functions should be left as is
        continue;
      }

      // If the function is other than "NoBlock" we want to reset the label function regardless
      // of whether there exists a "submit requirement".
      if (labelType.getFunction() != LabelFunction.NO_BLOCK) {
        section.setValue(labelType.toBuilder().setNoBlockFunction().build());
        updated = true;
      }

      Optional<SubmitRequirement> sr = createSrFromLabelDef(labelType);
      if (!sr.isPresent()) {
        continue;
      }
      // Make the operation idempotent by skipping creating the submit-requirement if one was
      // already created or previously existed.
      if (existingSubmitRequirements.containsKey(labelName)) {
        SubmitRequirement existing = existingSubmitRequirements.get(labelName);
        if (!sr.get().equals(existing)) {
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
      existingSubmitRequirements.put(sr.get());
    }
    return updated ? Status.MIGRATED : Status.NO_CHANGE;
  }

  private static Optional<SubmitRequirement> createSrFromLabelDef(LabelType lt) {
    if (isLabelSkipped(lt)) {
      return Optional.of(createNonApplicableSr(lt));
    } else if (isBlockingOrRequiredLabel(lt)) {
      return Optional.of(createBlockingOrRequiredSr(lt));
    }
    return Optional.empty();
  }

  private static SubmitRequirement createNonApplicableSr(LabelType lt) {
    return SubmitRequirement.builder()
        .setName(lt.getName())
        .setApplicabilityExpression(SubmitRequirementExpression.of("is:false"))
        .setSubmittabilityExpression(SubmitRequirementExpression.create("is:true"))
        .setAllowOverrideInChildProjects(lt.isCanOverride())
        .build();
  }

  /**
   * Create a "submit requirement" that is only satisfied if the label is voted with the max votes
   * and/or not voted by the min vote, according to the label attributes.
   */
  private static SubmitRequirement createBlockingOrRequiredSr(LabelType lt) {
    SubmitRequirement.Builder builder =
        SubmitRequirement.builder()
            .setName(lt.getName())
            .setAllowOverrideInChildProjects(lt.isCanOverride());
    String maxPart =
        String.format("label:%s=MAX", lt.getName())
            + (lt.isIgnoreSelfApproval() ? ",user=non_uploader" : "");
    switch (lt.getFunction()) {
      case MAX_WITH_BLOCK ->
          builder.setSubmittabilityExpression(
              SubmitRequirementExpression.create(
                  String.format("%s AND -label:%s=MIN", maxPart, lt.getName())));
      case ANY_WITH_BLOCK ->
          builder.setSubmittabilityExpression(
              SubmitRequirementExpression.create(String.format("-label:%s=MIN", lt.getName())));
      case MAX_NO_BLOCK ->
          builder.setSubmittabilityExpression(SubmitRequirementExpression.create(maxPart));
      case NO_BLOCK -> {}
      case NO_OP -> {}
      case PATCH_SET_LOCK -> {}
      default -> {}
    }
    ImmutableList<String> refPatterns = lt.getRefPatterns();
    if (refPatterns != null && !refPatterns.isEmpty()) {
      builder.setApplicabilityExpression(
          SubmitRequirementExpression.of(
              String.join(
                  " OR ",
                  lt.getRefPatterns().stream()
                      .map(b -> "branch:\\\"" + b + "\\\"")
                      .collect(Collectors.toList()))));
    }
    return builder.build();
  }

  private static boolean isBlockingOrRequiredLabel(LabelType lt) {
    return switch (lt.getFunction()) {
      case ANY_WITH_BLOCK, MAX_WITH_BLOCK, MAX_NO_BLOCK -> true;
      case NO_BLOCK, NO_OP, PATCH_SET_LOCK -> false;
    };
  }

  private static boolean isLabelSkipped(LabelType lt) {
    ImmutableList<LabelValue> values = lt.getValues();
    return values.isEmpty() || (values.size() == 1 && values.get(0).getValue() == 0);
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
      try (TreeWalk tw = TreeWalk.forPath(reader, RULES_PL_FILE, commit.getTree())) {
        if (tw != null) {
          return true;
        }
      }

      return false;
    }
  }

  private boolean hasMigrationAlreadyRun(Project.NameKey project) throws IOException {
    try (Repository repo = repoManager.openRepository(project)) {
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
  }

  /**
   * Helper "Map" to of submit requirements with case-preserving keys and case-insensitive lookup
   */
  private static class SubmitRequirementMap {
    private final Map<String, SubmitRequirement> submitRequirements;
    private final Map<String, String> lowerCaseToOriginalNames;

    SubmitRequirementMap(Map<String, SubmitRequirement> submitRequirements) {
      this.submitRequirements = submitRequirements;
      this.lowerCaseToOriginalNames =
          submitRequirements.keySet().stream()
              .collect(Collectors.toMap(k -> k.toLowerCase(Locale.ROOT), k -> k));
    }

    boolean containsKey(String name) {
      return lowerCaseToOriginalNames.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    SubmitRequirement get(String name) {
      String orig = lowerCaseToOriginalNames.get(name.toLowerCase(Locale.ROOT));
      return orig != null ? submitRequirements.get(orig) : null;
    }

    void put(SubmitRequirement sr) {
      String name = sr.name();
      submitRequirements.put(name, sr);
      lowerCaseToOriginalNames.put(name.toLowerCase(Locale.ROOT), name);
    }
  }
}
