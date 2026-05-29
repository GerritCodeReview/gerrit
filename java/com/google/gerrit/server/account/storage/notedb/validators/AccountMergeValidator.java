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

import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.validators.AccountValidator;
import com.google.gerrit.server.git.validators.MergeValidationException;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Repository;

public class AccountMergeValidator implements MergeValidationListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final AllUsersName allUsersName;
  private final ChangeData.Factory changeDataFactory;
  private final AccountValidator accountValidator;

  @Inject
  public AccountMergeValidator(
      AllUsersName allUsersName,
      ChangeData.Factory changeDataFactory,
      AccountValidator accountValidator) {
    this.allUsersName = allUsersName;
    this.changeDataFactory = changeDataFactory;
    this.accountValidator = accountValidator;
  }

  @Override
  public void onPreMerge(
      Repository repo,
      CodeReviewRevWalk revWalk,
      CodeReviewCommit commit,
      ProjectState destProject,
      BranchNameKey destBranch,
      PatchSet.Id patchSetId,
      IdentifiedUser caller)
      throws MergeValidationException {
    Account.Id accountId = Account.Id.fromRef(destBranch.branch());
    if (!allUsersName.equals(destProject.getNameKey()) || accountId == null) {
      return;
    }

    ChangeData cd =
        changeDataFactory.create(destProject.getProject().getNameKey(), patchSetId.changeId());
    try {
      if (!cd.currentFilePaths().contains(AccountProperties.ACCOUNT_CONFIG)) {
        return;
      }
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log("Cannot validate account update");
      throw new MergeValidationException("account validation unavailable", e);
    }

    try {
      List<String> errorMessages =
          accountValidator.validate(accountId, repo, revWalk, null, commit);
      if (!errorMessages.isEmpty()) {
        throw new MergeValidationException(
            "invalid account configuration: " + Joiner.on("; ").join(errorMessages));
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot validate account update");
      throw new MergeValidationException("account validation unavailable", e);
    }
  }
}
