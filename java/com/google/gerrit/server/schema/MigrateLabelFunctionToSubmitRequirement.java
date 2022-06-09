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
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class MigrateLabelFunctionToSubmitRequirement {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  public MigrateLabelFunctionToSubmitRequirement(
      ProjectCache projectCache,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory) {
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
  }

  public void execute() throws IOException, ConfigInvalidException {
    List<NameKey> allProjects = projectCache.all().stream().collect(Collectors.toList());
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

  private void executeMigration(Project.NameKey project)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    ProjectConfig config = projectConfigFactory.read(md);
    List<LabelType> labelTypes =
        config.getLabelSections().values().stream().collect(Collectors.toList());
    for (LabelType lt : labelTypes) {
      SubmitRequirement sr = convertToSubmitRequirement(lt);
      config.upsertSubmitRequirement(sr);
      lt.toBuilder().setFunction(LabelFunction.NO_BLOCK);
      config.upsertLabelType(lt);
    }
    commit(config, md);
  }

  private void commit(ProjectConfig projectConfig, MetaDataUpdate md) throws IOException {
    md.setMessage(String.format("Migrate label functions to submit requirements"));
    projectConfig.commit(md);
    projectCache.evict(projectConfig.getName());
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
    switch (lt.getFunction()) {
      case MAX_WITH_BLOCK:
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(
                String.format("-label:%s=MAX AND -label:%s=MIN", lt.getName(), lt.getName())));
        break;
      case ANY_WITH_BLOCK:
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(String.format("-label:%s=MIN", lt.getName())));
        break;
      case MAX_NO_BLOCK:
        builder.setSubmittabilityExpression(
            SubmitRequirementExpression.create(String.format("-label:%s=MAX", lt.getName())));
        break;
      default:
        break;
    }
    return builder.build();
  }

  private static boolean isLabelBlocking(LabelType labelType) {
    return labelType.getFunction().isBlock() || labelType.getFunction().isRequired();
  }

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
            return -1;
          }
          if (project2.parents().anyMatch(parent -> parent.getNameKey().equals(p1))) {
            return 1;
          }
          return 0;
        });
    return sorted;
  }

  private boolean anyProjectHasProlog(List<Project.NameKey> allProjects) {
    return allProjects.stream().anyMatch(p -> projectHasProlog(p));
  }

  private boolean projectHasProlog(Project.NameKey project) {
    Optional<ProjectState> projectStateOptional = projectCache.get(project);
    if (!projectStateOptional.isPresent()) {
      return false;
    }
    return projectStateOptional.get().hasPrologRules();
  }
}
