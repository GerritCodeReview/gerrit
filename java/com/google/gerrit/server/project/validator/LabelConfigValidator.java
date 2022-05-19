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

package com.google.gerrit.server.project.validator;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectLevelConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Validates modifications to label configurations in the {@code project.config} file that is stored
 * in {@code refs/meta/config}.
 */
@Singleton
public class LabelConfigValidator implements CommitValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicSet<LabelConfigValidatorChecker> labelConfigValidatorCheckers;
  private final DiffOperations diffOperations;

  @Inject
  public LabelConfigValidator(
      DiffOperations diffOperations,
      DynamicSet<LabelConfigValidatorChecker> labelConfigValidatorCheckers) {
    this.diffOperations = diffOperations;
    this.labelConfigValidatorCheckers = labelConfigValidatorCheckers;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    try {
      if (!receiveEvent.refName.equals(RefNames.REFS_CONFIG)
          || !isFileChanged(receiveEvent, ProjectConfig.PROJECT_CONFIG)) {
        // The project.config file in refs/meta/config was not modified, hence we do not need to do
        // any validation and can return early.
        return ImmutableList.of();
      }

      // Load the new config
      Config newConfig;
      try {
        newConfig = loadNewConfig(receiveEvent);
      } catch (ConfigInvalidException e) {
        // The current config is invalid, hence we cannot inspect the delta.
        // Rejecting invalid configs is not the responsibility of this validator, hence ignore this
        // exception here.
        logger.atWarning().log(
            "cannot inspect the project config, because parsing %s from revision %s"
                + " in project %s failed: %s",
            ProjectConfig.PROJECT_CONFIG,
            receiveEvent.commit.name(),
            receiveEvent.getProjectNameKey(),
            e.getMessage());
        return ImmutableList.of();
      }

      // Load the old config
      Config oldConfig = loadOldConfig(receiveEvent).orElse(null);

      // Run the checkers
      List<String> validationMessages = new ArrayList<>();
      for (LabelConfigValidatorChecker checker : labelConfigValidatorCheckers) {
        validationMessages.addAll(checker.validate(newConfig, oldConfig));
      }

      if (!validationMessages.isEmpty()) {
        throw new CommitValidationException(
            String.format(
                "invalid %s file in revision %s",
                ProjectConfig.PROJECT_CONFIG, receiveEvent.commit.getName()),
            asCommitValidationMessages(validationMessages));
      }
      return ImmutableList.of();
    } catch (IOException | DiffNotAvailableException e) {
      String errorMessage =
          String.format(
              "failed to validate file %s for revision %s in ref %s of project %s",
              ProjectConfig.PROJECT_CONFIG,
              receiveEvent.commit.getName(),
              RefNames.REFS_CONFIG,
              receiveEvent.getProjectNameKey());
      logger.atSevere().withCause(e).log("%s", errorMessage);
      throw new CommitValidationException(errorMessage, e);
    }
  }

  private static List<CommitValidationMessage> asCommitValidationMessages(List<String> messages) {
    return messages.stream()
        .map(s -> new CommitValidationMessage(s, ValidationMessage.Type.ERROR))
        .collect(Collectors.toList());
  }

  /**
   * Whether the given file was changed in the given revision.
   *
   * @param receiveEvent the receive event
   * @param fileName the name of the file
   */
  private boolean isFileChanged(CommitReceivedEvent receiveEvent, String fileName)
      throws DiffNotAvailableException {
    Map<String, FileDiffOutput> fileDiffOutputs;
    if (receiveEvent.commit.getParentCount() > 0) {
      // normal commit with one parent: use listModifiedFilesAgainstParent with parentNum = 1 to
      // compare against the only parent (using parentNum = 0 to compare against the default parent
      // would also work)
      // merge commit with 2 or more parents: must use listModifiedFilesAgainstParent with parentNum
      // = 1 to compare against the first parent (using parentNum = 0 would compare against the
      // auto-merge)
      fileDiffOutputs =
          diffOperations.listModifiedFilesAgainstParent(
              receiveEvent.getProjectNameKey(), receiveEvent.commit, 1, DiffOptions.DEFAULTS);
    } else {
      // initial commit: must use listModifiedFilesAgainstParent with parentNum = 0
      fileDiffOutputs =
          diffOperations.listModifiedFilesAgainstParent(
              receiveEvent.getProjectNameKey(),
              receiveEvent.commit,
              /* parentNum=*/ 0,
              DiffOptions.DEFAULTS);
    }
    return fileDiffOutputs.keySet().contains(fileName);
  }

  private Config loadNewConfig(CommitReceivedEvent receiveEvent)
      throws IOException, ConfigInvalidException {
    ProjectLevelConfig.Bare bareConfig = new ProjectLevelConfig.Bare(ProjectConfig.PROJECT_CONFIG);
    bareConfig.load(receiveEvent.project.getNameKey(), receiveEvent.revWalk, receiveEvent.commit);
    return bareConfig.getConfig();
  }

  private Optional<Config> loadOldConfig(CommitReceivedEvent receiveEvent) throws IOException {
    if (receiveEvent.commit.getParentCount() == 0) {
      // initial commit, an old config doesn't exist
      return Optional.empty();
    }

    try {
      ProjectLevelConfig.Bare bareConfig =
          new ProjectLevelConfig.Bare(ProjectConfig.PROJECT_CONFIG);
      bareConfig.load(
          receiveEvent.project.getNameKey(),
          receiveEvent.revWalk,
          receiveEvent.commit.getParent(0));
      return Optional.of(bareConfig.getConfig());
    } catch (ConfigInvalidException e) {
      // the old config is not parseable, treat this the same way as if an old config didn't exist
      // so that all parameters in the new config are validated
      logger.atWarning().log(
          "cannot inspect the old project config, because parsing %s from parent revision %s"
              + " in project %s failed: %s",
          ProjectConfig.PROJECT_CONFIG,
          receiveEvent.commit.name(),
          receiveEvent.getProjectNameKey(),
          e.getMessage());
      return Optional.empty();
    }
  }
}
