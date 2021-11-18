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
import com.google.gerrit.server.query.CommitEditsPredicate;
import com.google.gerrit.server.query.CommitEditsPredicate.CommitEditsArgs;
import com.google.inject.Inject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * A query builder for submit requirement expressions that includes all {@link ChangeQueryBuilder}
 * operators, in addition to extra operators contributed by this class.
 *
 * <p>Operators defined in this class cannot be used in change queries.
 */
public class SubmitRequirementChangeQueryBuilder extends ChangeQueryBuilder {

  private static final QueryBuilder.Definition<ChangeData, ChangeQueryBuilder> def =
      new QueryBuilder.Definition<>(SubmitRequirementChangeQueryBuilder.class);

  /**
   * Regular expression for the {@link #commitedits(String)} operator. Field value is of the form:
   *
   * <p>'$fileRegex',content='$contentRegex'
   *
   * <p>Both $fileRegex and $contentRegex may contain escaped single or double quotes.
   */
  private static final Pattern COMMIT_EDITS_PATTERN =
      Pattern.compile("'((?:(?:\\\\')|(?:[^']))*)',content='((?:(?:\\\\')|(?:[^']))*)'");

  private final CommitEditsPredicate.Factory commitEditsPredicateFactory;

  @Inject
  SubmitRequirementChangeQueryBuilder(
      Arguments args, CommitEditsPredicate.Factory commitEditsPredicateFactory) {
    super(def, args);
    this.commitEditsPredicateFactory = commitEditsPredicateFactory;
  }

  @Override
  protected void checkFieldAvailable(FieldDef<ChangeData, ?> field, String operator) {
    // Submit requirements don't rely on the index, so they can be used regardless of index schema
    // version.
  }

  @Override
  public Predicate<ChangeData> is(String value) throws QueryParseException {
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return new ConstantPredicate(value);
    }
    return super.is(value);
  }

  @Operator
  public Predicate<ChangeData> commitedits(String value) throws QueryParseException {
    Matcher matcher = COMMIT_EDITS_PATTERN.matcher(value);
    if (!matcher.find()) {
      throw new QueryParseException(
          "Argument for the 'commitEdits' operator should be in the form of 'fileRegex',content='contentRegex'.");
    }
    String fileRegex = matcher.group(1);
    String contentRegex = matcher.group(2);
    return commitEditsPredicateFactory.create(CommitEditsArgs.create(fileRegex, contentRegex));
  }
}
