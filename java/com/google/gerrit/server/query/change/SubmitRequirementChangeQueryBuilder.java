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

import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.FileEditsPredicate;
import com.google.gerrit.server.query.FileEditsPredicate.FileEditsArgs;
import com.google.inject.Inject;
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

  /**
   * Regular expression for the {@link #file(String)} operator. Field value is of the form:
   *
   * <p>'$fileRegex',withDiffContaining='$contentRegex'
   *
   * <p>Both $fileRegex and $contentRegex may contain escaped single or double quotes.
   */
  private static final Pattern FILE_EDITS_PATTERN =
      Pattern.compile("'((?:(?:\\\\')|(?:[^']))*)',withDiffContaining='((?:(?:\\\\')|(?:[^']))*)'");

  private final FileEditsPredicate.Factory fileEditsPredicateFactory;

  @Inject
  SubmitRequirementChangeQueryBuilder(
      Arguments args,
      DistinctVotersPredicate.Factory distinctVotersPredicateFactory,
      FileEditsPredicate.Factory fileEditsPredicateFactory) {
    super(def, args);
    this.distinctVotersPredicateFactory = distinctVotersPredicateFactory;
    this.fileEditsPredicateFactory = fileEditsPredicateFactory;
  }

  @Override
  protected void checkFieldAvailable(FieldDef<ChangeData, ?> field, String operator) {
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
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return new ConstantPredicate(value);
    }
    return super.is(value);
  }

  @Operator
  public Predicate<ChangeData> authoremail(String who) throws QueryParseException {
    return new RegexAuthorEmailPredicate(who);
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

  private static void validateRegularExpression(String pattern, String errorMessage)
      throws QueryParseException {
    try {
      Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      throw new QueryParseException(errorMessage, e);
    }
  }
}
