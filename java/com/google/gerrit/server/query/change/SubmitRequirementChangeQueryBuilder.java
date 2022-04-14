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

package com.google.gerrit.server.query.change;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.submitrequirement.predicate.ConstantPredicate;
import com.google.gerrit.server.submitrequirement.predicate.DistinctVotersPredicate;
import com.google.gerrit.server.submitrequirement.predicate.FileEditsPredicate;
import com.google.gerrit.server.submitrequirement.predicate.FileEditsPredicate.FileEditsArgs;
import com.google.gerrit.server.submitrequirement.predicate.HasSubmoduleUpdatePredicate;
import com.google.gerrit.server.submitrequirement.predicate.RegexAuthorEmailPredicate;
import com.google.gerrit.server.submitrequirement.predicate.RegexCommitterEmailPredicate;
import com.google.gerrit.server.submitrequirement.predicate.RegexUploaderEmailPredicateFactory;
import com.google.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A query builder for submit requirement expressions that includes all {@link ChangeQueryBuilder}
 * operators, in addition to extra operators contributed by this class.
 *
 * <p>Operators defined in this class cannot be used in change queries.
 */
public class SubmitRequirementChangeQueryBuilder extends ChangeQueryBuilder {

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> def =
      new QueryBuilder.Definition<>(SubmitRequirementChangeQueryBuilder.class);

  private final DistinctVotersPredicate.Factory distinctVotersPredicateFactory;
  private final HasSubmoduleUpdatePredicate.Factory hasSubmoduleUpdateFactory;

  /**
   * Regular expression for the {@link #file(String)} operator. Field value is of the form:
   *
   * <p>'$fileRegex',withDiffContaining='$contentRegex'
   *
   * <p>Both $fileRegex and $contentRegex may contain escaped single or double quotes.
   */
  private static final Pattern FILE_EDITS_PATTERN =
      Pattern.compile("'((?:(?:\\\\')|(?:[^']))*)',withDiffContaining='((?:(?:\\\\')|(?:[^']))*)'");

  public static final String SUBMODULE_UPDATE_HAS_ARG = "submodule-update";
  private static final Splitter SUBMODULE_UPDATE_SPLITTER = Splitter.on(",");

  private final FileEditsPredicate.Factory fileEditsPredicateFactory;
  private final RegexUploaderEmailPredicateFactory regexUploaderEmailPredicateFactory;

  private static final ImmutableSet<String> UNSUPPORTED_HAS_OPERANDS =
      ImmutableSet.of("draft", "attention", "edit");

  private static final ImmutableSet<String> UNSUPPORTED_IS_OPERANDS =
      ImmutableSet.of("submittable", "attention", "owner", "uploader", "reviewer", "cc");

  @Inject
  SubmitRequirementChangeQueryBuilder(
      Arguments args,
      DistinctVotersPredicate.Factory distinctVotersPredicateFactory,
      FileEditsPredicate.Factory fileEditsPredicateFactory,
      HasSubmoduleUpdatePredicate.Factory hasSubmoduleUpdateFactory,
      RegexUploaderEmailPredicateFactory regexUploaderEmailPredicateFactory) {
    super(def, args);
    this.distinctVotersPredicateFactory = distinctVotersPredicateFactory;
    this.fileEditsPredicateFactory = fileEditsPredicateFactory;
    this.hasSubmoduleUpdateFactory = hasSubmoduleUpdateFactory;
    this.regexUploaderEmailPredicateFactory = regexUploaderEmailPredicateFactory;
  }

  @Override
  protected void checkFieldAvailable(SchemaField<ChangeData, ?> field, String operator) {
    // Submit requirements don't rely on the index, so they can be used regardless of index schema
    // version.
  }

  @Override
  public Predicate<ChangeData> is(String value) throws QueryParseException {
    if ("submittable".equalsIgnoreCase(value)) {
      throw new QueryParseException(
          String.format(
              "Operator 'is:submittable' cannot be used in submit requirement expressions."));
    }
    if (UNSUPPORTED_IS_OPERANDS.contains(value)) {
      throw new QueryParseException(
          String.format(
              "Operator 'is:%s' cannot be used in submit requirement expressions.", value));
    }
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return new ConstantPredicate(value);
    }
    return super.is(value);
  }

  @Override
  public Predicate<ChangeData> has(String value) throws QueryParseException {
    if (UNSUPPORTED_HAS_OPERANDS.contains(value)) {
      throw new QueryParseException(
          String.format(
              "Operator 'has:%s' cannot be used in submit requirement expressions.", value));
    }
    if (value.toLowerCase(Locale.US).startsWith(SUBMODULE_UPDATE_HAS_ARG)) {
      List<String> args = SUBMODULE_UPDATE_SPLITTER.splitToList(value);
      if (args.size() > 2) {
        throw error(
            String.format(
                "wrong number of arguments for the has:%s operator", SUBMODULE_UPDATE_HAS_ARG));
      } else if (args.size() == 2) {
        List<String> baseValue = Splitter.on("=").splitToList(args.get(1));
        if (baseValue.size() != 2) {
          throw error("unexpected base value format");
        }
        if (!baseValue.get(0).toLowerCase(Locale.US).equals("base")) {
          throw error("unexpected base value format");
        }
        try {
          int base = Integer.parseInt(baseValue.get(1));
          return hasSubmoduleUpdateFactory.create(base);
        } catch (NumberFormatException e) {
          throw error(
              String.format(
                  "failed to parse the parent number %s: %s", baseValue.get(1), e.getMessage()));
        }
      } else {
        return hasSubmoduleUpdateFactory.create(0);
      }
    }
    return super.has(value);
  }

  @Operator
  public Predicate<ChangeData> authoremail(String who) throws QueryParseException {
    return new RegexAuthorEmailPredicate(who);
  }

  @Operator
  public Predicate<ChangeData> committerEmail(String who) throws QueryParseException {
    return new RegexCommitterEmailPredicate(who);
  }

  @Operator
  public Predicate<ChangeData> uploaderEmail(String who) throws QueryParseException {
    return regexUploaderEmailPredicateFactory.create(who);
  }

  @Operator
  public Predicate<ChangeData> distinctvoters(String value) throws QueryParseException {
    return distinctVotersPredicateFactory.create(value);
  }

  /**
   * A SR operator that can match with file path and content pattern. The value should be of the
   * form:
   *
   * <p>file:"'$filePattern',withDiffContaining='$contentPattern'"
   *
   * <p>The operator matches with changes that have their latest PS vs. base diff containing a file
   * path matching the {@code filePattern} with an edit (added, deleted, modified) matching the
   * {@code contentPattern}. {@code filePattern} and {@code contentPattern} can start with "^" to
   * use regular expression matching.
   *
   * <p>If the specified value does not match this form, we fall back to the operator's
   * implementation in {@link ChangeQueryBuilder}.
   */
  @Override
  public Predicate<ChangeData> file(String value) throws QueryParseException {
    Matcher matcher = FILE_EDITS_PATTERN.matcher(value);
    if (!matcher.find()) {
      return super.file(value);
    }
    String filePattern = matcher.group(1);
    String contentPattern = matcher.group(2);
    if (filePattern.startsWith("^")) {
      validateRegularExpression(filePattern, "Invalid file pattern.");
    }
    if (contentPattern.startsWith("^")) {
      validateRegularExpression(contentPattern, "Invalid content pattern.");
    }
    return fileEditsPredicateFactory.create(FileEditsArgs.create(filePattern, contentPattern));
  }

  @Override
  protected void validateLabelArgs(Set<Account.Id> accountIds) throws QueryParseException {}

  private static void validateRegularExpression(String pattern, String errorMessage)
      throws QueryParseException {
    try {
      Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      throw new QueryParseException(errorMessage, e);
    }
  }
}
