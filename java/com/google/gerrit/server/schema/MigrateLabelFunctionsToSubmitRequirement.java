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
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
 * <p>If there is an existing label and there exists a "submit requirement" with the same name, then
 * the logic will leave the "submit requirement" as is and will not replace it. But in this case,
 * the label will be reset to NO_BLOCK anyway.
 */
public class MigrateLabelFunctionsToSubmitRequirement {
  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;

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
   */
  public void executeMigration(Project.NameKey project, UpdateUI ui)
      throws IOException, ConfigInvalidException {
    if (hasPrologRules(project)) {
      ui.message(String.format("Skipping project %s because it has prolog rules", project));
      return;
    }
    ProjectLevelConfig.Bare projectConfig =
        new ProjectLevelConfig.Bare(ProjectConfig.PROJECT_CONFIG);
    try (Repository repo = repoManager.openRepository(project);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, project, repo)) {
      projectConfig.load(project, repo);
      Config cfg = projectConfig.getConfig();
      Map<String, LabelAttributes> labelTypes = getLabelTypes(cfg);
      Set<String> submitRequirementNames = cfg.getSubsections(ProjectConfig.SUBMIT_REQUIREMENT);
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
        // Make the operation idempotent by skipping creating the submit-requirement if one was
        // already created or previously existed.
        if (submitRequirementNames.contains(labelName)) {
          ui.message(
              String.format(
                  "Skipping creating a submit requirement for label '%s' because one is "
                      + "already present. Please double check that the existing "
                      + "submit requirement is working as intended.",
                  labelName));
          continue;
        }
        updated = true;
        SubmitRequirement sr = createSrFromLabelDef(labelName, attributes);
        writeSubmitRequirement(cfg, sr);
      }
      if (updated) {
        commit(projectConfig, md);
      }
    }
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
      LabelAttributes attributes =
          LabelAttributes.create(
              function == null ? "MaxWithBlock" : function, canOverride, ignoreSelfApproval);
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
    md.setMessage("Migrate label functions to submit requirements\n");
    projectConfig.commit(md);
  }

  private static SubmitRequirement createSrFromLabelDef(
      String labelName, LabelAttributes attributes) {
    if (!isLabelBlocking(attributes.function())) {
      return createNonApplicableSr(labelName, attributes.canOverride());
    }
    return createBlockingSr(labelName, attributes);
  }

  private static SubmitRequirement createNonApplicableSr(String labelName, boolean canOverride) {
    return SubmitRequirement.builder()
        .setName(labelName)
        .setApplicabilityExpression(SubmitRequirementExpression.of("is:false"))
        .setSubmittabilityExpression(SubmitRequirementExpression.create("is:true"))
        .setAllowOverrideInChildProjects(canOverride)
        .build();
  }

  private static SubmitRequirement createBlockingSr(String labelName, LabelAttributes attributes) {
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
    return builder.build();
  }

  private static boolean isLabelBlocking(String function) {
    return function.equals("AnyWithBlock")
        || function.equals("MaxWithBlock")
        || function.equals("MaxNoBlock");
  }

  public boolean anyProjectHasProlog(Collection<NameKey> allProjects) throws IOException {
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
      RevCommit commit = repo.parseCommit(refsConfig.getObjectId());
      try (TreeWalk tw = TreeWalk.forPath(reader, "rules.pl", commit.getTree())) {
        if (tw != null) {
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

    static LabelAttributes create(
        String function, boolean canOverride, boolean ignoreSelfApproval) {
      return new AutoValue_MigrateLabelFunctionsToSubmitRequirement_LabelAttributes(
          function, canOverride, ignoreSelfApproval);
    }
  }
}
