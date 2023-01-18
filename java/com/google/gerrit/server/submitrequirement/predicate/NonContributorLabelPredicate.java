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

package com.google.gerrit.server.submitrequirement.predicate;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.account.AccountPredicates;
import com.google.gerrit.server.query.account.AccountQueryProcessor;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;

/**
 * A submit-requirement predicate that returns true if the latest patch set of the change has a
 * patch set label vote with value {@link #expectedValue} from a Gerrit account who's not the latest
 * commit author, committer or uploader.
 */
public class NonContributorLabelPredicate extends SubmitRequirementPredicate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectCache projectCache;
  private final Provider<AccountQueryProcessor> queryProcessorProvider;

  private final String labelName;
  private final int expectedValue;

  public interface Factory {
    NonContributorLabelPredicate create(String labelName, int expectedValue);
  }

  @Inject
  public NonContributorLabelPredicate(
      ProjectCache projectCache,
      Provider<AccountQueryProcessor> queryProcessorProvider,
      @Assisted String labelName,
      @Assisted int expectedValue) {
    super("labelSr:" + labelName, Integer.toString(expectedValue));
    this.projectCache = projectCache;
    this.queryProcessorProvider = queryProcessorProvider;
    this.labelName = labelName;
    this.expectedValue = expectedValue;
  }

  @Override
  public boolean match(ChangeData cd) {
    if (cd.change() == null) {
      return false;
    }
    Optional<ProjectState> project = projectCache.get(cd.change().getDest().project());
    if (!project.isPresent()) {
      return false;
    }
    LabelType labelType = type(project.get().getLabelTypes(), labelName);
    if (labelType == null) {
      return false; // Label is not defined by this project.
    }
    boolean hasVote = false;
    for (PatchSetApproval p : cd.currentApprovals()) {
      if (labelType.matches(p)) {
        hasVote = true;
        if (match(cd, p.value(), p.accountId())) {
          return true;
        }
      }
    }
    if (!hasVote && expectedValue == 0) {
      return true;
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Nullable
  private static LabelType type(LabelTypes types, String toFind) {
    if (types.byLabel(toFind).isPresent()) {
      return types.byLabel(toFind).get();
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getName())) {
        return lt;
      }
    }
    return null;
  }

  private boolean match(ChangeData cd, short value, Account.Id approver) {
    return !(value != expectedValue
        || cd.currentPatchSet().uploader().equals(approver)
        || matchAccount(cd.getCommitter().getEmailAddress(), approver)
        || matchAccount(cd.getAuthor().getEmailAddress(), approver));
  }

  /**
   * Returns true if the {@code email} parameter belongs to the account identified by the {@code
   * accountId} parameter.
   */
  private boolean matchAccount(String email, Account.Id accountId) {
    try {
      ImmutableList<AccountState> committerAccounts =
          queryProcessorProvider.get().query(AccountPredicates.preferredEmail(email)).entities();
      return committerAccounts.stream().anyMatch(c -> c.account().id().equals(accountId));
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log("Failed to parse account query");
    }
    return false;
  }
}
