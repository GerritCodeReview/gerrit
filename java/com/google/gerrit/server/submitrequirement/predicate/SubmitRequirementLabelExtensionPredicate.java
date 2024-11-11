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

package com.google.gerrit.server.submitrequirement.predicate;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Enums;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gerrit.server.query.change.LabelPredicate;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extensions of the {@link LabelPredicate} that are only available for submit requirement
 * expressions, but not for search.
 *
 * <p>Supported extensions:
 *
 * <ul>
 *   <li>"users=human_reviewers" arg, e.g. "label:Code-Review=MAX,users=human_reviewers" matches
 *       changes where all human reviewers have approved the change with Code-Review=MAX
 * </ul>
 */
public class SubmitRequirementLabelExtensionPredicate extends SubmitRequirementPredicate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    SubmitRequirementLabelExtensionPredicate create(String value) throws QueryParseException;
  }

  private static final Pattern PATTERN = Pattern.compile("(?<label>[^,]*),users=human_reviewers$");
  private static final Pattern PATTERN_LABEL =
      Pattern.compile("(?<label>[^,<>=]*)(?<op>=|<=|>=|<|>)(?<value>[^,]*)");

  public static boolean matches(String value) {
    return PATTERN.matcher(value).matches();
  }

  public static void validateIfNoMatch(String value) throws QueryParseException {
    if (value.contains(",users=")) {
      throw new QueryParseException(
          "Cannot use the 'users' argument in conjunction with other arguments ('count', 'user',"
              + " group')");
    }
  }

  private final Arguments args;
  private final ServiceUserClassifier serviceUserClassifier;
  private final String label;

  @Inject
  SubmitRequirementLabelExtensionPredicate(
      Arguments args, ServiceUserClassifier serviceUserClassifier, @Assisted String value)
      throws QueryParseException {
    super("label", value);
    this.args = args;
    this.serviceUserClassifier = serviceUserClassifier;

    Matcher m = PATTERN.matcher(value);
    if (!m.matches()) {
      throw new QueryParseException(
          String.format("invalid value for '%s': %s", getOperator(), value));
    }
    this.label = validateLabel(m.group("label"));
  }

  @CanIgnoreReturnValue
  private String validateLabel(String label) throws QueryParseException {
    int eq = label.indexOf('=');

    if (eq <= 0) {
      return label;
    }

    String statusName = label.substring(eq + 1).toUpperCase(Locale.US);
    SubmitRecord.Label.Status status =
        Enums.getIfPresent(SubmitRecord.Label.Status.class, statusName).orNull();
    if (status != null) {
      // We would need to use SubmitRecordPredicate but can't because it doesn't implement
      // Matchable.
      throw new QueryParseException(
          "Cannot use the 'users=human_reviewers' argument in conjunction with a submit record"
              + " label status");
    }
    return label;
  }

  @Override
  public boolean match(ChangeData cd) {
    if (!cd.reviewersByEmail().byState(ReviewerStateInternal.REVIEWER).isEmpty()
        && !matchZeroVotes(label)) {
      // Reviewers by email are reviewers that don't have a Gerrit account. Without Gerrit
      // account they cannot vote on the change, which means changes that have any such
      // reviewers never match when we expect a vote != 0 from all reviewers.
      logger.atFine().log(
          "change %s doesn't match since there are reviewers by email"
              + " (that don't have a matching approval): %s",
          cd.change().getChangeId(), cd.reviewersByEmail().byState(ReviewerStateInternal.REVIEWER));
      return false;
    }

    ImmutableSet<Account.Id> humanReviewers =
        cd.reviewers().byState(ReviewerStateInternal.REVIEWER).stream()
            // Ignore the change owner (if the change owner voted on their own change they are
            // technically a reviewer).
            .filter(accountId -> !accountId.equals(cd.change().getOwner()))
            // Ignore reviewers that are service users.
            .filter(accountId -> !serviceUserClassifier.isServiceUser(accountId))
            .collect(toImmutableSet());

    if (humanReviewers.isEmpty()) {
      // a review from human reviewers is required, but no human reviewers are present
      return false;
    }

    for (Account.Id reviewer : humanReviewers) {
      if (!new LabelPredicate(
              args,
              label,
              ImmutableSet.of(reviewer),
              /* group= */ null,
              /* count= */ null,
              /* countOp= */ null)
          .match(cd)) {
        logger.atFine().log(
            "change %s doesn't match because it misses matching approvals from: %s",
            cd.change().getChangeId(), reviewer);
        return false;
      }
    }

    logger.atFine().log(
        "change %s matches because it has matching approvals from all human reviewers: %s",
        cd.change().getChangeId(), humanReviewers);
    return true;
  }

  private boolean matchZeroVotes(String label) {
    Matcher m = PATTERN_LABEL.matcher(label);
    if (!m.matches()) {
      return false;
    }

    String op = m.group("op");
    String value = m.group("value");

    Optional<Integer> intValue = Optional.ofNullable(Ints.tryParse(value));

    if (op.equals("=") && (intValue.isPresent() && intValue.get() == 0)) {
      return true;
    } else if (op.equals("<=")) {
      if (intValue.isPresent() && intValue.get() >= 0) {
        return true;
      } else if (value.equals("MAX")) {
        return true;
      }
      return false;
    } else if (op.equals("<")) {
      if (intValue.isPresent() && intValue.get() > 0) {
        return true;
      } else if (value.equals("MAX")) {
        return true;
      }
    } else if (op.equals(">=")) {
      if (intValue.isPresent() && intValue.get() <= 0) {
        return true;
      } else if (value.equals("MIN")) {
        return true;
      }
      return false;
    } else if (op.equals(">")) {
      if (intValue.isPresent() && intValue.get() < 0) {
        return true;
      } else if (value.equals("MIN")) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
