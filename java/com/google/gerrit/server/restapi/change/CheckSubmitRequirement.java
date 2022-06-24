// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SubmitRequirementsJson;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

/**
 * A rest view to evaluate (test) a {@link com.google.gerrit.entities.SubmitRequirement} on a given
 * change. The submit requirement can be supplied in one of two ways:
 *
 * <p>1) Using the {@link SubmitRequirementInput}.
 *
 * <p>2) From a change to the {@link RefNames#REFS_CONFIG} branch and the name of the
 * submit-requirement.
 *
 * <p>TODO(ghareeb): Can this class be made singleton?
 */
public class CheckSubmitRequirement
    implements RestModifyView<ChangeResource, SubmitRequirementInput> {
  private final SubmitRequirementsEvaluator evaluator;

  private String srName;
  private Integer refsConfigChangeNumber;
  private final GitRepositoryManager repoManager;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ChangeData.Factory changeDataFactory;

  @Option(name = "--sr-name")
  public void setSrName(String srName) {
    this.srName = srName;
  }

  @Option(name = "--refs-config-change-number")
  public void setRefsConfigChangeNumber(Integer refsConfigChangeNumber) {
    this.refsConfigChangeNumber = refsConfigChangeNumber;
  }

  @Inject
  public CheckSubmitRequirement(
      SubmitRequirementsEvaluator evaluator,
      GitRepositoryManager repoManager,
      ProjectConfig.Factory projectConfigFactory,
      ChangeData.Factory changeDataFactory) {
    this.evaluator = evaluator;
    this.repoManager = repoManager;
    this.projectConfigFactory = projectConfigFactory;
    this.changeDataFactory = changeDataFactory;
  }

  @Override
  public Response<SubmitRequirementResultInfo> apply(
      ChangeResource resource, SubmitRequirementInput input)
      throws BadRequestException, AuthException, PermissionBackendException {
    SubmitRequirement requirement =
        srName != null && refsConfigChangeNumber != null
            ? createSubmitRequirementFromRequestParams(resource)
            : createSubmitRequirement(input);
    SubmitRequirementResult res =
        evaluator.evaluateRequirement(requirement, resource.getChangeData());
    return Response.ok(SubmitRequirementsJson.toInfo(requirement, res));
  }

  private SubmitRequirement createSubmitRequirement(SubmitRequirementInput input)
      throws BadRequestException {
    validateSubmitRequirementInput(input);
    return SubmitRequirement.builder()
        .setName(input.name)
        .setDescription(Optional.ofNullable(input.description))
        .setApplicabilityExpression(SubmitRequirementExpression.of(input.applicabilityExpression))
        .setSubmittabilityExpression(
            SubmitRequirementExpression.create(input.submittabilityExpression))
        .setOverrideExpression(SubmitRequirementExpression.of(input.overrideExpression))
        .setAllowOverrideInChildProjects(
            input.allowOverrideInChildProjects == null ? true : input.allowOverrideInChildProjects)
        .build();
  }

  /**
   * Loads the submit-requirement identified by the name {@link #srName} from the latest patch-set
   * of the change with number {@link #refsConfigChangeNumber}.
   *
   * @return a {@link SubmitRequirement} entity.
   * @throws BadRequestException If {@link #refsConfigChangeNumber} is a non-existent change or not
   *     in the {@link RefNames#REFS_CONFIG} branch, if the submit-requirement with name {@link
   *     #srName} does not exist or if the server failed to load the project due to other
   *     exceptions.
   */
  private SubmitRequirement createSubmitRequirementFromRequestParams(ChangeResource rsrc)
      throws BadRequestException {
    ObjectId revisionId = ObjectId.zeroId();
    Project.NameKey project = rsrc.getProject();
    try (Repository git = repoManager.openRepository(project)) {
      ChangeData changeData = changeDataFactory.create(project, Change.id(refsConfigChangeNumber));
      if (!changeData.change().getDest().branch().equals(RefNames.REFS_CONFIG)) {
        throw new BadRequestException(
            String.format(
                "Change with number '%d' is not in refs/meta/config branch.",
                refsConfigChangeNumber));
      }
      revisionId = changeData.currentPatchSet().commitId();
      ProjectConfig cfg = projectConfigFactory.create(project);
      cfg.load(git, revisionId);
      List<Entry<String, SubmitRequirement>> submitRequirements =
          cfg.getSubmitRequirementSections().entrySet().stream()
              .filter(entry -> entry.getKey().equals(srName))
              .collect(Collectors.toList());
      if (submitRequirements.isEmpty()) {
        throw new BadRequestException(
            String.format("No submit requirement matching name '%s'", srName));
      }
      return Iterables.getOnlyElement(submitRequirements).getValue();
    } catch (IOException | ConfigInvalidException e) {
      throw new BadRequestException(
          String.format(
              "Failed to load project config for change '%d' from revision '%s'",
              refsConfigChangeNumber, revisionId),
          e);
    } catch (StorageException e) {
      if (e.getCause() instanceof NoSuchChangeException) {
        throw new BadRequestException(
            String.format("Change '%d' does not exist", refsConfigChangeNumber), e);
      }
      throw e;
    }
  }

  private void validateSubmitRequirementInput(SubmitRequirementInput input)
      throws BadRequestException {
    if (input.name == null) {
      throw new BadRequestException("Field 'name' is missing from input.");
    }
    if (input.submittabilityExpression == null) {
      throw new BadRequestException("Field 'submittability_expression' is missing from input.");
    }
  }
}
