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

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResolver;
import com.google.inject.Inject;
import java.util.Arrays;

public class ApprovalQueryBuilder extends QueryBuilder<ApprovalContext, ApprovalQueryBuilder> {
  private static final QueryBuilder.Definition<ApprovalContext, ApprovalQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ApprovalQueryBuilder.class);

  private final MagicValuePredicate.Factory magicValuePredicate;
  private final UserInPredicate.Factory userInPredicate;
  private final GroupResolver groupResolver;
  private final GroupControl.Factory groupControl;
  private final ListOfFilesUnchangedPredicate listOfFilesUnchangedPredicate;

  @Inject
  protected ApprovalQueryBuilder(
      MagicValuePredicate.Factory magicValuePredicate,
      UserInPredicate.Factory userInPredicate,
      GroupResolver groupResolver,
      GroupControl.Factory groupControl,
      ListOfFilesUnchangedPredicate listOfFilesUnchangedPredicate) {
    super(mydef, null);
    this.magicValuePredicate = magicValuePredicate;
    this.userInPredicate = userInPredicate;
    this.groupResolver = groupResolver;
    this.groupControl = groupControl;
    this.listOfFilesUnchangedPredicate = listOfFilesUnchangedPredicate;
  }

  @Operator
  public Predicate<ApprovalContext> changeKind(String term) throws QueryParseException {
    return new ChangeKindPredicate(toEnumValue(ChangeKind.class, term));
  }

  @Operator
  public Predicate<ApprovalContext> is(String term) throws QueryParseException {
    return magicValuePredicate.create(toEnumValue(MagicValuePredicate.MagicValue.class, term));
  }

  @Operator
  public Predicate<ApprovalContext> approverin(String group) throws QueryParseException {
    return userInPredicate.create(UserInPredicate.Field.APPROVER, parseGroupOrThrow(group));
  }

  @Operator
  public Predicate<ApprovalContext> uploaderin(String group) throws QueryParseException {
    return userInPredicate.create(UserInPredicate.Field.UPLOADER, parseGroupOrThrow(group));
  }

  @Operator
  public Predicate<ApprovalContext> has(String value) throws QueryParseException {
    if (value.equals("unchanged-files")) {
      return listOfFilesUnchangedPredicate;
    }
    throw error(
        String.format(
            "'%s' is not a supported argument for has. only 'unchanged-files' is supported",
            value));
  }

  private static <T extends Enum<T>> T toEnumValue(Class<T> clazz, String term)
      throws QueryParseException {
    try {
      return Enum.valueOf(clazz, term.toUpperCase().replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new QueryParseException(
          String.format(
              "%s is not a valid term. valid options: %s",
              term, Arrays.asList(clazz.getEnumConstants(), e)));
    }
  }

  private AccountGroup.UUID parseGroupOrThrow(String maybeUUID) throws QueryParseException {
    GroupDescription.Basic g = groupResolver.parseId(maybeUUID);
    if (g == null || !groupControl.controlFor(g).isVisible()) {
      throw error("Group " + maybeUUID + " not found");
    }
    return g.getGroupUUID();
  }
}
