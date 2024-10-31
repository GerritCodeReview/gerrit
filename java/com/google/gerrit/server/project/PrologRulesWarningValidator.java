// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.gerrit.server.project.ProjectConfig.RULES_PL_FILE;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A validator than emits a warning for newly added prolog rules file via git push. Modification and
 * deletion are allowed so that clients can get rid of prolog rules.
 */
@Singleton
public class PrologRulesWarningValidator implements CommitValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DiffOperations diffOperations;

  @Inject
  public PrologRulesWarningValidator(DiffOperations diffOperations) {
    this.diffOperations = diffOperations;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    try {
      if (receiveEvent.refName.equals(RefNames.REFS_CONFIG)
          && isFileAdded(receiveEvent, RULES_PL_FILE)) {
        return ImmutableList.of(
            new CommitValidationMessage(
                "Uploading a new 'rules.pl' file is discouraged."
                    + " Please consider adding submit-requirements instead.",
                ValidationMessage.Type.WARNING));
      }
    } catch (DiffNotAvailableException e) {
      logger.atWarning().withCause(e).log("Failed to retrieve the file diff.");
    }
    return ImmutableList.of();
  }

  private boolean isFileAdded(CommitReceivedEvent receiveEvent, String fileName)
      throws DiffNotAvailableException {
    List<Map.Entry<String, FileDiffOutput>> matchingEntries =
        diffOperations
            .listModifiedFilesAgainstParent(
                receiveEvent.project.getNameKey(),
                receiveEvent.commit,
                /* parentNum= */ 0,
                DiffOptions.DEFAULTS)
            .entrySet()
            .stream()
            .filter(e -> fileName.equals(e.getKey()))
            .collect(Collectors.toList());
    if (matchingEntries.size() != 1) {
      return false;
    }
    return matchingEntries.iterator().next().getValue().changeType().equals(ChangeType.ADDED);
  }
}
