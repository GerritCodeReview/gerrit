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
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.QueryParseException;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
public class SubmitRequirementExpressionsValidator implements CommitValidationListener {
  private final DiffOperations diffOperations;
  private final ProjectCache projectCache;
  private final ProjectConfig.Factory projectConfigFactory;
  private final SubmitRequirementsEvaluator submitRequirementsEvaluator;

  @Inject
  SubmitRequirementExpressionsValidator(
      DiffOperations diffOperations,
      ProjectCache projectCache,
      ProjectConfig.Factory projectConfigFactory,
      SubmitRequirementsEvaluator submitRequirementsEvaluator) {
    this.diffOperations = diffOperations;
    this.projectCache = projectCache;
    this.projectConfigFactory = projectConfigFactory;
    this.submitRequirementsEvaluator = submitRequirementsEvaluator;
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
      // Union the list of macros currently being pushed with those defined in All-Projects:
      // For example, this could be a push to a normal project, but the submit requirements could
      // be referencing macros from All-Projects.
      Map<String, String> macros = projectConfig.getMacrosSection();
      macros.putAll(projectCache.getAllProjects().getMacros());
      ImmutableList<CommitValidationMessage> macroValidationMessages = validateMacros(macros);
      if (!macroValidationMessages.isEmpty()) {
        throw new CommitValidationException(
            String.format(
                "invalid macro definitions in %s (revision = %s)",
                ProjectConfig.PROJECT_CONFIG, projectConfig.getRevision()),
            macroValidationMessages);
      }
      ImmutableList<CommitValidationMessage> srValidationMessage =
          validateSubmitRequirementExpressions(
              projectConfig.getSubmitRequirementSections().values(), macros);
      if (!srValidationMessage.isEmpty()) {
        throw new CommitValidationException(
            String.format(
                "invalid submit requirement expressions in %s (revision = %s)",
                ProjectConfig.PROJECT_CONFIG, projectConfig.getRevision()),
            srValidationMessage);
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

  /** Validate macro expressions. Macros should be valid submit requirement (sub)-expressions. */
  private ImmutableList<CommitValidationMessage> validateMacros(
      Map<String, String> macroExpressions) {
    List<CommitValidationMessage> validationMessages = new ArrayList<>();
    for (Map.Entry<String, String> macroDefinition : macroExpressions.entrySet()) {
      String macroName = macroDefinition.getKey();
      String macroExpression = macroDefinition.getValue();
      try {
        submitRequirementsEvaluator.validateExpression(
            SubmitRequirementExpression.create(macroExpression));
      } catch (QueryParseException e) {
        if (validationMessages.isEmpty()) {
          validationMessages.add(
              new CommitValidationMessage(
                  "Invalid project configuration", ValidationMessage.Type.ERROR));
        }
        validationMessages.add(
            new CommitValidationMessage(
                String.format(
                    "  %s: Expression '%s' of macro name '%s' is not a valid submit requirement"
                        + " expression: %s",
                    ProjectConfig.PROJECT_CONFIG, macroExpression, macroName, e.getMessage()),
                ValidationMessage.Type.ERROR));
      }
    }
    return ImmutableList.copyOf(validationMessages);
  }

  private ImmutableList<CommitValidationMessage> validateSubmitRequirementExpressions(
      Collection<SubmitRequirement> submitRequirements, Map<String, String> macros) {
    List<CommitValidationMessage> validationMessages = new ArrayList<>();
    for (SubmitRequirement submitRequirement : submitRequirements) {
      validateSubmitRequirementExpression(
          validationMessages,
          submitRequirement,
          submitRequirement.submittabilityExpression(),
          ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
          macros);
      submitRequirement
          .applicabilityExpression()
          .ifPresent(
              expression ->
                  validateSubmitRequirementExpression(
                      validationMessages,
                      submitRequirement,
                      expression,
                      ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION,
                      macros));
      submitRequirement
          .overrideExpression()
          .ifPresent(
              expression ->
                  validateSubmitRequirementExpression(
                      validationMessages,
                      submitRequirement,
                      expression,
                      ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION,
                      macros));
    }
    return ImmutableList.copyOf(validationMessages);
  }

  private void validateSubmitRequirementExpression(
      List<CommitValidationMessage> validationMessages,
      SubmitRequirement submitRequirement,
      SubmitRequirementExpression expression,
      String configKey,
      Map<String, String> macros) {
    try {
      expression = submitRequirementsEvaluator.expandExpression(expression, macros);
      submitRequirementsEvaluator.validateExpression(expression);
    } catch (QueryParseException e) {
      if (validationMessages.isEmpty()) {
        validationMessages.add(
            new CommitValidationMessage(
                "Invalid project configuration", ValidationMessage.Type.ERROR));
      }
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "  %s: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                      + " invalid: %s",
                  ProjectConfig.PROJECT_CONFIG,
                  expression.expressionString(),
                  submitRequirement.name(),
                  ProjectConfig.SUBMIT_REQUIREMENT,
                  submitRequirement.name(),
                  configKey,
                  e.getMessage()),
              ValidationMessage.Type.ERROR));
    } catch (StorageException e) {
      if (validationMessages.isEmpty()) {
        validationMessages.add(
            new CommitValidationMessage(
                "Invalid project configuration", ValidationMessage.Type.ERROR));
      }
      validationMessages.add(
          new CommitValidationMessage(
              String.format(
                  "  %s: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                      + " invalid: Expression is referencing non-existing macros: %s",
                  ProjectConfig.PROJECT_CONFIG,
                  expression.expressionString(),
                  submitRequirement.name(),
                  ProjectConfig.SUBMIT_REQUIREMENT,
                  submitRequirement.name(),
                  configKey,
                  e.getMessage()),
              ValidationMessage.Type.ERROR));
    }
  }
}
