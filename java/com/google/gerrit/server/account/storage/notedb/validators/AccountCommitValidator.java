// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.account.storage.notedb.validators;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.AccountValidator;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.Repository;

/**
 * Validates that pending account updates in NoteDb are valid according to {@link AccountValidator}.
 */
public class AccountCommitValidator implements CommitValidationListener {

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final AccountValidator accountValidator;

  @Inject
  AccountCommitValidator(
      GitRepositoryManager repoManager, AllUsersName allUsers, AccountValidator accountValidator) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.accountValidator = accountValidator;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    if (!allUsers.equals(receiveEvent.project.getNameKey())) {
      return Collections.emptyList();
    }

    if (receiveEvent.command.getRefName().startsWith(MagicBranch.NEW_CHANGE)) {
      // no validation on push for review, will be checked on submit by
      // MergeValidators.AccountMergeValidator
      return Collections.emptyList();
    }

    Account.Id accountId = Account.Id.fromRef(receiveEvent.refName);
    if (accountId == null) {
      return Collections.emptyList();
    }

    try (Repository repo = repoManager.openRepository(allUsers)) {
      List<String> errorMessages =
          accountValidator.validate(
              accountId,
              repo,
              receiveEvent.revWalk,
              receiveEvent.command.getOldId(),
              receiveEvent.commit);
      if (!errorMessages.isEmpty()) {
        throw new CommitValidationException(
            "invalid account configuration",
            errorMessages.stream()
                .map(m -> new CommitValidationMessage(m, ValidationMessage.Type.ERROR))
                .collect(toList()));
      }
    } catch (IOException e) {
      throw new CommitValidationException(
          String.format("Validating update for account %s failed", accountId.get()), e);
    }
    return Collections.emptyList();
  }
}
