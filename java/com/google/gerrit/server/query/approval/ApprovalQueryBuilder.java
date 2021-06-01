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

package com.google.gerrit.server.query.approval;

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.inject.Inject;
import java.util.Arrays;

public class ApprovalQueryBuilder extends QueryBuilder<ApprovalContext, ApprovalQueryBuilder> {
  private static final QueryBuilder.Definition<ApprovalContext, ApprovalQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ApprovalQueryBuilder.class);

  private final ChangeKindPredicate.Factory changeKindPredicateFactory;
  private final MagicValuePredicate.Factory magicValuePredicate;

  @Inject
  protected ApprovalQueryBuilder(
      ChangeKindPredicate.Factory changeKindPredicateFactory,
      MagicValuePredicate.Factory magicValuePredicate) {
    super(mydef, null);
    this.changeKindPredicateFactory = changeKindPredicateFactory;
    this.magicValuePredicate = magicValuePredicate;
  }

  @Operator
  public Predicate<ApprovalContext> changeKind(String term) throws QueryParseException {
    return changeKindPredicateFactory.create(toEnumValue(ChangeKind.class, term));
  }

  @Operator
  public Predicate<ApprovalContext> is(String term) throws QueryParseException {
    return magicValuePredicate.create(toEnumValue(MagicValuePredicate.MagicValue.class, term));
  }

  private static <T extends Enum<T>> T toEnumValue(Class<T> clazz, String term)
      throws QueryParseException {
    try {
      return Enum.valueOf(clazz, term.toUpperCase().replace('-', '_'));
    } catch (
        @SuppressWarnings("UnusedException")
        IllegalArgumentException unused) {
      throw new QueryParseException(
          String.format(
              "%s is not a valid term. valid options: %s",
              term, Arrays.asList(clazz.getEnumConstants())));
    }
  }
}
