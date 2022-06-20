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
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * We only run the migration if all projects in this gerrit installation have no Prolog
 * submittability rules (i.e. a rules.pl file in refs/meta/config).
 */
public class MigrateLabelFunctionsToSubmitRequirement {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  public MigrateLabelFunctionsToSubmitRequirement(
      ProjectCache projectCache,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory) {
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
  }

  public void execute() throws IOException, ConfigInvalidException {
    List<Project.NameKey> allProjects = projectCache.all().stream().collect(Collectors.toList());
    boolean hasProlog = anyProjectHasProlog(allProjects);
    if (hasProlog) {
      logger.atInfo().log(
          "Skipping the migration because this gerrit installation has prolog rules");
      return;
    }
    List<Project.NameKey> sortedProjects = sortProjectsWithInheritance(allProjects);
    for (Project.NameKey project : sortedProjects) {
      executeMigration(project);
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
    boolean updated = false;
    for (LabelType lt : labelTypes) {
      // Make the operation idempotent by skipping creating the submit-requirement if one was
      // already created or previously existed.
      if (hasSubmitRequirement(project, lt.getName())) {
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
    projectCache.evictAndReindex(projectConfig.getName());
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

  /**
   * Projects should be processed bottom up, i.e. child projects should be migrated first before
   * their parent projects. This method sorts the list of input projects with respect to their
   * inheritance hierarchy.
   *
   * @param projects a list of input projects.
   * @return the same list of input projects sorted with respect to the inheritance hierarchy. Child
   *     projects come first before their parent projects.
   */
  private List<Project.NameKey> sortProjectsWithInheritance(List<Project.NameKey> projects) {
    List<Project.NameKey> sorted = new ArrayList<>(projects);
    Collections.sort(
        sorted,
        (p1, p2) -> {
          Optional<ProjectState> projectState1 = projectCache.get(p1);
          Optional<ProjectState> projectState2 = projectCache.get(p2);
          if (!projectState1.isPresent()) {
            return -1;
          }
          if (!projectState2.isPresent()) {
            return 1;
          }
          ProjectState project1 = projectState1.get();
          ProjectState project2 = projectState2.get();
          if (project1.parents().anyMatch(parent -> parent.getNameKey().equals(p2))) {
            // project 1 is a child of project 2.
            return -1;
          }
          if (project2.parents().anyMatch(parent -> parent.getNameKey().equals(p1))) {
            // project 2 is a child of project 1.
            return 1;
          }
          return 0;
        });
    return sorted;
  }

  private boolean hasSubmitRequirement(Project.NameKey project, String srName) {
    Optional<ProjectState> projectState = projectCache.get(project);
    if (!projectState.isPresent()) {
      return false;
    }
    return projectState.get().getSubmitRequirements().containsKey(srName.toLowerCase(Locale.ROOT));
  }

  private boolean anyProjectHasProlog(List<Project.NameKey> allProjects) {
    return allProjects.stream().anyMatch(p -> hasPrologRules(p));
  }

  private boolean hasPrologRules(Project.NameKey project) {
    Optional<ProjectState> projectStateOptional = projectCache.get(project);
    if (!projectStateOptional.isPresent()) {
      return false;
    }
    return projectStateOptional.get().hasPrologRules();
  }
}
