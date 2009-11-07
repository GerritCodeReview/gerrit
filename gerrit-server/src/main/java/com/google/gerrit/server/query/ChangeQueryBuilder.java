// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.query;

import com.google.gerrit.reviewdb.RevId;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

/**
 * Parses a query string meant to be applied to change objects.
 * <p>
 * This class is thread-safe, and may be reused across threads to parse queries.
 */
@Singleton
public class ChangeQueryBuilder extends QueryBuilder {
  public static final String FIELD_CHANGE = "change";
  public static final String FIELD_COMMIT = "commit";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_OWNER = "owner";

  private static final String CHANGE_RE = "^[1-9][0-9]*$";
  private static final String COMMIT_RE =
      "^([0-9a-fA-F]{4," + RevId.LEN + "})$";

  @Operator
  public Predicate change(final String value) {
    match(value, CHANGE_RE);
    return new OperatorPredicate(FIELD_CHANGE, value);
  }

  @Operator
  public Predicate commit(final String value) {
    final AbbreviatedObjectId id = AbbreviatedObjectId.fromString(value);
    return new ObjectIdPredicate(FIELD_COMMIT, id);
  }

  @Operator
  public Predicate owner(final String value) {
    return new OperatorPredicate(FIELD_OWNER, value);
  }

  @Operator
  public Predicate reviewer(final String value) {
    return new OperatorPredicate(FIELD_REVIEWER, value);
  }

  @Override
  protected Predicate defaultField(final String value)
      throws QueryParseException {
    if (value.matches(CHANGE_RE)) {
      return change(value);

    } else if (value.matches(COMMIT_RE)) {
      return commit(value);

    } else {
      throw error("Unsupported query:" + value);
    }
  }

  private static void match(String val, String re) {
    if (!val.matches(re)) {
      throw new IllegalArgumentException("Invalid value :" + val);
    }
  }
}
