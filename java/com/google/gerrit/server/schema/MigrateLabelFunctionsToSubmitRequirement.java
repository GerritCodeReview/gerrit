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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * A class with logic for migrating existing label functions to submit requirements and converting
 * the label functions to {@link LabelFunction#NO_BLOCK}. This is meant to run as a schema migration
 * so that it's executed on server upgrades.
 *
 * <p>The migration is only run if all projects in this gerrit installation have no Prolog submit
 * rules (i.e. no rules.pl file in refs/meta/config). If at least one project has prolog submit
 * rules the migration is skipped.
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
 */
public class MigrateLabelFunctionsToSubmitRequirement {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final GitRepositoryManager repoManager;

  @Inject
  public MigrateLabelFunctionsToSubmitRequirement(
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      GitRepositoryManager repoManager) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.repoManager = repoManager;
  }

  public void execute() throws IOException, ConfigInvalidException {
    Collection<Project.NameKey> allProjects = repoManager.list();
    if (anyProjectHasProlog(allProjects)) {
      logger.atInfo().log("Skipping the migration because at least one project has prolog rules");
      return;
    }
    for (Project.NameKey project : allProjects) {
      try {
        executeMigration(project);
      } catch (ConfigInvalidException e) {
        logger.atWarning().withCause(e).log(
            String.format(
                "Failed to migrate label functions to submit requirements for project %s.",
                project));
      }
    }
  }

  /**
   * For each label function, create a corresponding submit-requirement and set the label function
   * to NO_BLOCK. Blocking label functions are substituted with blocking submit-requirements.
   * Non-blocking label functions are substituted with non-applicable submit requirements, allowing
   * the label vote to be surfaced as a trigger vote (optional label).
   */
  private void executeMigration(Project.NameKey project)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    ProjectConfig config = projectConfigFactory.read(md);
    List<LabelType> labelTypes =
        config.getLabelSections().values().stream().collect(Collectors.toList());
    Set<String> submitRequirementNames = config.getSubmitRequirementSections().keySet();
    boolean updated = false;
    for (LabelType lt : labelTypes) {
      // Make the operation idempotent by skipping creating the submit-requirement if one was
      // already created or previously existed.
      if (submitRequirementNames.contains(lt.getName())) {
        continue;
      }
      if (lt.getFunction().equals(LabelFunction.PATCH_SET_LOCK)) {
        // PATCH_SET_LOCK functions should be left as is
        continue;
      }
      updated = true;
      SubmitRequirement sr = convertToSubmitRequirement(lt);
      config.upsertSubmitRequirement(sr);
      LabelType updatedLt = lt.toBuilder().setFunction(LabelFunction.NO_BLOCK).build();
      config.upsertLabelType(updatedLt);
    }
    if (updated) {
      commit(config, md);
    }
  }

  private void commit(ProjectConfig projectConfig, MetaDataUpdate md) throws IOException {
    md.setMessage(String.format("Migrate label functions to submit requirements"));
    projectConfig.commit(md);
  }

  private static SubmitRequirement convertToSubmitRequirement(LabelType lt) {
    if (!isLabelBlocking(lt)) {
      return createNonApplicableSr(lt);
    }
    return createBlockingSr(lt);
  }

  private static SubmitRequirement createNonApplicableSr(LabelType lt) {
    return SubmitRequirement.builder()
        .setName(lt.getName())
        .setApplicabilityExpression(SubmitRequirementExpression.of("is:false"))
        .setSubmittabilityExpression(SubmitRequirementExpression.create("is:true"))
        .setAllowOverrideInChildProjects(lt.isCanOverride())
        .build();
  }

  private static SubmitRequirement createBlockingSr(LabelType lt) {
    SubmitRequirement.Builder builder =
        SubmitRequirement.builder()
            .setName(lt.getName())
            .setAllowOverrideInChildProjects(lt.isCanOverride());
    String maxPart =
        String.format("label:%s=MAX", lt.getName())
            + (lt.isIgnoreSelfApproval() ? ",user=non_uploader" : "");
    switch (lt.getFunction()) {
      case MAX_WITH_BLOCK:
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(
                String.format("%s AND -label:%s=MIN", maxPart, lt.getName())));
        break;
      case ANY_WITH_BLOCK:
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(String.format("-label:%s=MIN", lt.getName())));
        break;
      case MAX_NO_BLOCK:
        builder.setSubmittabilityExpression(SubmitRequirementExpression.create(maxPart));
        break;
      default:
        break;
    }
    return builder.build();
  }

  private static boolean isLabelBlocking(LabelType labelType) {
    return labelType.getFunction().isBlock() || labelType.getFunction().isRequired();
  }

  private boolean anyProjectHasProlog(Collection<NameKey> allProjects)
      throws IOException, ConfigInvalidException {
    for (Project.NameKey p : allProjects) {
      if (hasPrologRules(p)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasPrologRules(Project.NameKey project)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    ProjectConfig config = projectConfigFactory.read(md);
    return config.getRulesId() != null;
  }
}
