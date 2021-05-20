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

package com.google.gerrit.server.project;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Validates the expressions of submit requirements in {@code project.config}.
 *
 * <p>Other validation of submit requirements is done in {@link ProjectConfig}, see {@code
 * ProjectConfig#loadSubmitRequirementSections(Config)}.
 *
 * <p>The validation of the expressions cannot be in {@link ProjectConfig} as it requires injecting
 * {@link SubmitRequirementsEvaluator} and we cannot do injections into {@link ProjectConfig} (since
 * {@link ProjectConfig} is cached in the project cache).
 */
public class SubmitRequirementConfigValidator implements CommitValidationListener {
  private final DiffOperations diffOperations;
  private final ProjectConfig.Factory projectConfigFactory;
  private final SubmitRequirementExpressionValidator submitRequirementExpressionValidator;

  @Inject
  SubmitRequirementConfigValidator(
      DiffOperations diffOperations,
      ProjectConfig.Factory projectConfigFactory,
      SubmitRequirementExpressionValidator submitRequirementExpressionValidator) {
    this.diffOperations = diffOperations;
    this.projectConfigFactory = projectConfigFactory;
    this.submitRequirementExpressionValidator = submitRequirementExpressionValidator;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent event)
      throws CommitValidationException {
    try {
      if (!event.refName.equals(RefNames.REFS_CONFIG)
          || !isFileChanged(event, ProjectConfig.PROJECT_CONFIG)) {
        // the project.config file in refs/meta/config was not modified, hence we do not need to
        // validate the submit requirements in it
        return ImmutableList.of();
      }

      ProjectConfig projectConfig = getProjectConfig(event);
      ImmutableList.Builder<String> validationMsgsBuilder = ImmutableList.builder();
      for (SubmitRequirement submitRequirement :
          projectConfig.getSubmitRequirementSections().values()) {
        validationMsgsBuilder.addAll(
            submitRequirementExpressionValidator.validateExpressions(submitRequirement));
      }
      ImmutableList<String> validationMsgs = validationMsgsBuilder.build();
      if (!validationMsgs.isEmpty()) {
        throw new CommitValidationException(
            String.format(
                "invalid submit requirement expressions in %s (revision = %s)",
                ProjectConfig.PROJECT_CONFIG, projectConfig.getRevision()),
            new ImmutableList.Builder<CommitValidationMessage>()
                .add(
                    new CommitValidationMessage(
                        "Invalid project configuration", ValidationMessage.Type.ERROR))
                .addAll(
                    validationMsgs.stream()
                        .map(m -> toCommitValidationMessage(m))
                        .collect(Collectors.toList()))
                .build());
      }
      return ImmutableList.of();
    } catch (IOException | DiffNotAvailableException | ConfigInvalidException e) {
      throw new CommitValidationException(
          String.format(
              "failed to validate submit requirement expressions in %s for revision %s in ref %s"
                  + " of project %s",
              ProjectConfig.PROJECT_CONFIG,
              event.commit.getName(),
              RefNames.REFS_CONFIG,
              event.project.getNameKey()),
          e);
    }
  }

  private static CommitValidationMessage toCommitValidationMessage(String message) {
    return new CommitValidationMessage(message, ValidationMessage.Type.ERROR);
  }

  /**
   * Whether the given file was changed in the given revision.
   *
   * @param receiveEvent the receive event
   * @param fileName the name of the file
   */
  private boolean isFileChanged(CommitReceivedEvent receiveEvent, String fileName)
      throws DiffNotAvailableException {
    return diffOperations
        .listModifiedFilesAgainstParent(
            receiveEvent.project.getNameKey(),
            receiveEvent.commit,
            /* parentNum=*/ 0,
            DiffOptions.DEFAULTS)
        .keySet().stream()
        .anyMatch(fileName::equals);
  }

  private ProjectConfig getProjectConfig(CommitReceivedEvent receiveEvent)
      throws IOException, ConfigInvalidException {
    ProjectConfig projectConfig = projectConfigFactory.create(receiveEvent.project.getNameKey());
    projectConfig.load(receiveEvent.revWalk, receiveEvent.commit);
    return projectConfig;
  }
}
