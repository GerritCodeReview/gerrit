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

package com.google.gerrit.server.project;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.meta.VersionedConfigFile;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * Validates modifications to label configurations in the {@code project.config} file that is stored
 * in {@code refs/meta/config}.
 *
 * <p>Rejects setting/changing deprecated fields that are no longer supported (fields {@code
 * copyAnyScore}, {@code copyMinScore}, {@code copyMaxScore}, {@code copyAllScoresIfNoChange},
 * {@code copyAllScoresIfNoCodeChange}, {@code copyAllScoresOnMergeFirstParentUpdate}, {@code
 * copyAllScoresOnTrivialRebase}, {@code copyAllScoresIfListOfFilesDidNotChange}, {@code
 * copyValue}).
 *
 * <p>Updates that unset the deprecated fields or that don't touch them are allowed.
 */
@Singleton
public class LabelConfigValidator implements CommitValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting public static final String KEY_COPY_ANY_SCORE = "copyAnyScore";

  @VisibleForTesting public static final String KEY_COPY_MIN_SCORE = "copyMinScore";

  @VisibleForTesting public static final String KEY_COPY_MAX_SCORE = "copyMaxScore";

  @VisibleForTesting public static final String KEY_COPY_VALUE = "copyValue";

  @VisibleForTesting
  public static final String KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE =
      "copyAllScoresOnMergeFirstParentUpdate";

  @VisibleForTesting
  public static final String KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE = "copyAllScoresOnTrivialRebase";

  @VisibleForTesting
  public static final String KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE = "copyAllScoresIfNoCodeChange";

  @VisibleForTesting
  public static final String KEY_COPY_ALL_SCORES_IF_NO_CHANGE = "copyAllScoresIfNoChange";

  @VisibleForTesting
  public static final String KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE =
      "copyAllScoresIfListOfFilesDidNotChange";

  // Map of deprecated boolean flags to the predicates that should be used in the copy condition
  // instead.
  private static final ImmutableMap<String, String> DEPRECATED_FLAGS =
      ImmutableMap.<String, String>builder()
          .put(KEY_COPY_ANY_SCORE, "is:ANY")
          .put(KEY_COPY_MIN_SCORE, "is:MIN")
          .put(KEY_COPY_MAX_SCORE, "is:MAX")
          .put(KEY_COPY_ALL_SCORES_IF_NO_CHANGE, "changekind:" + ChangeKind.NO_CHANGE.name())
          .put(
              KEY_COPY_ALL_SCORES_IF_NO_CODE_CHANGE,
              "changekind:" + ChangeKind.NO_CODE_CHANGE.name())
          .put(
              KEY_COPY_ALL_SCORES_ON_MERGE_FIRST_PARENT_UPDATE,
              "changekind:" + ChangeKind.MERGE_FIRST_PARENT_UPDATE.name())
          .put(
              KEY_COPY_ALL_SCORES_ON_TRIVIAL_REBASE,
              "changekind:" + ChangeKind.TRIVIAL_REBASE.name())
          .put(KEY_COPY_ALL_SCORES_IF_LIST_OF_FILES_DID_NOT_CHANGE, "has:unchanged-files")
          .build();

  private final ApprovalQueryBuilder approvalQueryBuilder;

  public LabelConfigValidator(ApprovalQueryBuilder approvalQueryBuilder) {
    this.approvalQueryBuilder = approvalQueryBuilder;
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

      ImmutableList.Builder<CommitValidationMessage> validationMessageBuilder =
          ImmutableList.builder();

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
      @Nullable Config oldConfig = loadOldConfig(receiveEvent).orElse(null);

      for (String labelName : newConfig.getSubsections(ProjectConfig.LABEL)) {
        rejectSettingDeprecatedFlags(newConfig, oldConfig, labelName, validationMessageBuilder);
        rejectSettingCopyValues(newConfig, oldConfig, labelName, validationMessageBuilder);
        rejectSettingBlockingFunction(newConfig, oldConfig, labelName, validationMessageBuilder);
        rejectDeletingFunction(newConfig, oldConfig, labelName, validationMessageBuilder);
        rejectNonParseableCopyCondition(newConfig, oldConfig, labelName, validationMessageBuilder);
      }

      ImmutableList<CommitValidationMessage> validationMessages = validationMessageBuilder.build();
      if (validationMessages.stream().anyMatch(CommitValidationMessage::isError)) {
        throw new CommitValidationException(
            String.format(
                "invalid %s file in revision %s",
                ProjectConfig.PROJECT_CONFIG, receiveEvent.commit.getName()),
            validationMessages);
      }
      return validationMessages;
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

  private void rejectSettingDeprecatedFlags(
      Config newConfig,
      @Nullable Config oldConfig,
      String labelName,
      ImmutableList.Builder<CommitValidationMessage> validationMessageBuilder) {
    for (String deprecatedFlag : DEPRECATED_FLAGS.keySet()) {
      if (flagChangedOrNewlySet(newConfig, oldConfig, labelName, deprecatedFlag)) {
        validationMessageBuilder.add(
            new CommitValidationMessage(
                String.format(
                    "Parameter '%s.%s.%s' is deprecated and cannot be set,"
                        + " use '%s' in '%s.%s.%s' instead.",
                    ProjectConfig.LABEL,
                    labelName,
                    deprecatedFlag,
                    DEPRECATED_FLAGS.get(deprecatedFlag),
                    ProjectConfig.LABEL,
                    labelName,
                    ProjectConfig.KEY_COPY_CONDITION),
                ValidationMessage.Type.ERROR));
      }
    }
  }

  private void rejectSettingCopyValues(
      Config newConfig,
      @Nullable Config oldConfig,
      String labelName,
      ImmutableList.Builder<CommitValidationMessage> validationMessageBuilder) {
    if (copyValuesChangedOrNewlySet(newConfig, oldConfig, labelName)) {
      validationMessageBuilder.add(
          new CommitValidationMessage(
              String.format(
                  "Parameter '%s.%s.%s' is deprecated and cannot be set,"
                      + " use 'is:<copy-value>' in '%s.%s.%s' instead.",
                  ProjectConfig.LABEL,
                  labelName,
                  KEY_COPY_VALUE,
                  ProjectConfig.LABEL,
                  labelName,
                  ProjectConfig.KEY_COPY_CONDITION),
              ValidationMessage.Type.ERROR));
    }
  }

  private void rejectSettingBlockingFunction(
      Config newConfig,
      @Nullable Config oldConfig,
      String labelName,
      ImmutableList.Builder<CommitValidationMessage> validationMessageBuilder) {
    if (flagChangedOrNewlySet(newConfig, oldConfig, labelName, ProjectConfig.KEY_FUNCTION)) {
      String fnName =
          newConfig.getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_FUNCTION);
      Optional<LabelFunction> labelFn = LabelFunction.parse(fnName);
      if (labelFn.isPresent() && !isLabelFunctionAllowed(labelFn.get())) {
        validationMessageBuilder.add(
            new CommitValidationMessage(
                String.format(
                    "Value '%s' of '%s.%s.%s' is not allowed and cannot be set."
                        + " Label functions can only be set to {%s, %s, %s}."
                        + " Use submit requirements instead of label functions.",
                    fnName,
                    ProjectConfig.LABEL,
                    labelName,
                    ProjectConfig.KEY_FUNCTION,
                    LabelFunction.NO_BLOCK,
                    LabelFunction.NO_OP,
                    LabelFunction.PATCH_SET_LOCK),
                ValidationMessage.Type.ERROR));
      }
    }
  }

  private void rejectDeletingFunction(
      Config newConfig,
      @Nullable Config oldConfig,
      String labelName,
      ImmutableList.Builder<CommitValidationMessage> validationMessageBuilder) {
    // Ban deletion of label function since the default is MaxWithBlock which is deprecated
    if (flagDeleted(newConfig, oldConfig, labelName, ProjectConfig.KEY_FUNCTION)) {
      validationMessageBuilder.add(
          new CommitValidationMessage(
              String.format(
                  "Cannot delete '%s.%s.%s'."
                      + " Label functions can only be set to {%s, %s, %s}."
                      + " Use submit requirements instead of label functions.",
                  ProjectConfig.LABEL,
                  labelName,
                  ProjectConfig.KEY_FUNCTION,
                  LabelFunction.NO_BLOCK,
                  LabelFunction.NO_OP,
                  LabelFunction.PATCH_SET_LOCK),
              ValidationMessage.Type.ERROR));
    }
  }

  private void rejectNonParseableCopyCondition(
      Config newConfig,
      @Nullable Config oldConfig,
      String labelName,
      ImmutableList.Builder<CommitValidationMessage> validationMessageBuilder) {
    if (flagChangedOrNewlySet(newConfig, oldConfig, labelName, ProjectConfig.KEY_COPY_CONDITION)) {
      String copyCondition =
          newConfig.getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_CONDITION);
      try {
        @SuppressWarnings("unused")
        var unused = approvalQueryBuilder.parse(copyCondition);
      } catch (QueryParseException e) {
        validationMessageBuilder.add(
            new CommitValidationMessage(
                String.format(
                    "Cannot parse copy condition '%s' of label %s (parameter '%s.%s.%s'): %s",
                    copyCondition,
                    labelName,
                    ProjectConfig.LABEL,
                    labelName,
                    ProjectConfig.KEY_COPY_CONDITION,
                    e.getMessage()),
                // if the old copy condition is not parseable allow updating it even if the new copy
                // condition is also not parseable, only emit a warning in this case
                hasUnparseableOldCopyCondition(oldConfig, labelName)
                    ? ValidationMessage.Type.WARNING
                    : ValidationMessage.Type.ERROR));
      }
    }
  }

  private boolean hasUnparseableOldCopyCondition(@Nullable Config oldConfig, String labelName) {
    if (oldConfig == null) {
      return false;
    }

    String oldCopyCondition =
        oldConfig.getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_COPY_CONDITION);
    try {
      @SuppressWarnings("unused")
      var unused = approvalQueryBuilder.parse(oldCopyCondition);
      return false;
    } catch (QueryParseException e) {
      return true;
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
    Map<String, ModifiedFile> fileDiffOutputs;
    if (receiveEvent.commit.getParentCount() > 0) {
      // normal commit with one parent: use listModifiedFilesAgainstParent with parentNum = 1 to
      // compare against the only parent (using parentNum = 0 to compare against the default parent
      // would also work)
      // merge commit with 2 or more parents: must use listModifiedFilesAgainstParent with parentNum
      // = 1 to compare against the first parent (using parentNum = 0 would compare against the
      // auto-merge)
      fileDiffOutputs =
          receiveEvent.diffOperations.loadModifiedFilesAgainstParentIfNecessary(
              receiveEvent.getProjectNameKey(),
              receiveEvent.commit,
              1,
              /* enableRenameDetection= */ true);
    } else {
      // initial commit: must use listModifiedFilesAgainstParent with parentNum = 0
      fileDiffOutputs =
          receiveEvent.diffOperations.loadModifiedFilesAgainstParentIfNecessary(
              receiveEvent.getProjectNameKey(),
              receiveEvent.commit,
              /* parentNum= */ 0,
              /* enableRenameDetection= */ true);
    }
    return fileDiffOutputs.keySet().contains(fileName);
  }

  private Config loadNewConfig(CommitReceivedEvent receiveEvent)
      throws IOException, ConfigInvalidException {
    VersionedConfigFile bareConfig = new VersionedConfigFile(ProjectConfig.PROJECT_CONFIG);
    bareConfig.load(receiveEvent.project.getNameKey(), receiveEvent.revWalk, receiveEvent.commit);
    return bareConfig.getConfig();
  }

  private Optional<Config> loadOldConfig(CommitReceivedEvent receiveEvent) throws IOException {
    if (receiveEvent.commit.getParentCount() == 0) {
      // initial commit, an old config doesn't exist
      return Optional.empty();
    }

    try {
      VersionedConfigFile bareConfig = new VersionedConfigFile(ProjectConfig.PROJECT_CONFIG);
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

  private static boolean flagChangedOrNewlySet(
      Config newConfig, @Nullable Config oldConfig, String labelName, String key) {
    if (oldConfig == null) {
      return newConfig.getNames(ProjectConfig.LABEL, labelName).contains(key);
    }

    // Use getString rather than getBoolean so that we do not have to deal with values that cannot
    // be parsed as a boolean.
    String oldValue = oldConfig.getString(ProjectConfig.LABEL, labelName, key);
    String newValue = newConfig.getString(ProjectConfig.LABEL, labelName, key);
    return newValue != null && !newValue.equals(oldValue);
  }

  private static boolean flagDeleted(
      Config newConfig, @Nullable Config oldConfig, String labelName, String key) {
    if (oldConfig == null) {
      return false;
    }
    String oldValue = oldConfig.getString(ProjectConfig.LABEL, labelName, key);
    String newValue = newConfig.getString(ProjectConfig.LABEL, labelName, key);
    return oldValue != null && newValue == null;
  }

  private static boolean copyValuesChangedOrNewlySet(
      Config newConfig, @Nullable Config oldConfig, String labelName) {
    if (oldConfig == null) {
      return newConfig.getNames(ProjectConfig.LABEL, labelName).contains(KEY_COPY_VALUE);
    }

    // Ignore the order in which the copy values are defined in the new and old config, since the
    // order doesn't matter for this parameter.
    ImmutableSet<String> oldValues =
        ImmutableSet.copyOf(
            oldConfig.getStringList(ProjectConfig.LABEL, labelName, KEY_COPY_VALUE));
    ImmutableSet<String> newValues =
        ImmutableSet.copyOf(
            newConfig.getStringList(ProjectConfig.LABEL, labelName, KEY_COPY_VALUE));
    return !newValues.isEmpty() && !Sets.difference(newValues, oldValues).isEmpty();
  }

  private static boolean isLabelFunctionAllowed(LabelFunction labelFunction) {
    return labelFunction.equals(LabelFunction.NO_BLOCK)
        || labelFunction.equals(LabelFunction.NO_OP)
        || labelFunction.equals(LabelFunction.PATCH_SET_LOCK);
  }
}
