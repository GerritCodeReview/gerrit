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

import static java.util.stream.Collectors.joining;

import com.google.common.base.Enums;
import com.google.common.primitives.Ints;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Singleton
public class ApprovalQueryBuilder extends QueryBuilder<ApprovalContext, ApprovalQueryBuilder> {
  private static final QueryBuilder.Definition<ApprovalContext, ApprovalQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ApprovalQueryBuilder.class);

  public static class ChangeIsPredicate extends OperatorPredicate<ApprovalContext>
      implements Matchable<ApprovalContext> {
    private final Predicate<ChangeData> delegate;

    public ChangeIsPredicate(Predicate<ChangeData> delegate, String value) {
      super("changeis", value);
      this.delegate = delegate;
    }

    @Override
    public boolean match(ApprovalContext approvalContext) {
      return delegate.asMatchable().match(approvalContext.changeData());
    }

    @Override
    public int getCost() {
      return delegate.asMatchable().getCost();
    }
  }

  private final MagicValuePredicate.Factory magicValuePredicate;
  private final UserInPredicate.Factory userInPredicate;
  private final GroupResolver groupResolver;
  private final GroupControl.Factory groupControl;
  private final ListOfFilesUnchangedPredicate listOfFilesUnchangedPredicate;
  private final ChangeQueryBuilder changeQueryBuilder;

  @Inject
  protected ApprovalQueryBuilder(
      MagicValuePredicate.Factory magicValuePredicate,
      UserInPredicate.Factory userInPredicate,
      GroupResolver groupResolver,
      GroupControl.Factory groupControl,
      ListOfFilesUnchangedPredicate listOfFilesUnchangedPredicate,
      ChangeQueryBuilder changeQueryBuilder) {
    super(mydef, null);
    this.magicValuePredicate = magicValuePredicate;
    this.userInPredicate = userInPredicate;
    this.groupResolver = groupResolver;
    this.groupControl = groupControl;
    this.listOfFilesUnchangedPredicate = listOfFilesUnchangedPredicate;
    this.changeQueryBuilder = changeQueryBuilder;
  }

  @Operator
  public Predicate<ApprovalContext> changekind(String value) throws QueryParseException {
    return parseEnumValue(ChangeKind.class, value)
        .map(ChangeKindPredicate::new)
        .orElseThrow(
            () ->
                new QueryParseException(
                    String.format(
                        "%s is not a valid value for operator 'changekind'. Valid values: %s",
                        value, formatEnumValues(ChangeKind.class))));
  }

  @Operator
  public Predicate<ApprovalContext> is(String value) throws QueryParseException {
    // try to parse exact value
    Optional<Integer> exactValue = Optional.ofNullable(Ints.tryParse(value));
    if (exactValue.isPresent()) {
      return new ExactValuePredicate(exactValue.get().shortValue());
    }

    // try to parse magic value
    Optional<MagicValuePredicate.MagicValue> magicValue =
        parseEnumValue(MagicValuePredicate.MagicValue.class, value);
    if (magicValue.isPresent()) {
      return magicValuePredicate.create(magicValue.get());
    }

    // it's neither an exact value nor a magic value
    throw new QueryParseException(
        String.format(
            "%s is not a valid value for operator 'is'. Valid values: %s or integer",
            value, formatEnumValues(MagicValuePredicate.MagicValue.class)));
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
            "'%s' is not a valid value for operator 'has'."
                + " The only valid value is 'unchanged-files'.",
            value));
  }

  @Operator
  public Predicate<ApprovalContext> changeis(String value) throws QueryParseException {
    Predicate<ChangeData> changePredicate = changeQueryBuilder.is(value);
    return new ChangeIsPredicate(changePredicate, value);
  }

  private static <T extends Enum<T>> Optional<T> parseEnumValue(Class<T> clazz, String value) {
    return Optional.ofNullable(
        Enums.getIfPresent(clazz, value.toUpperCase(Locale.US).replace('-', '_')).orNull());
  }

  private <T extends Enum<T>> String formatEnumValues(Class<T> clazz) {
    return Arrays.stream(clazz.getEnumConstants())
        .map(Object::toString)
        .sorted()
        .collect(joining(", "));
  }

  private AccountGroup.UUID parseGroupOrThrow(String maybeUUID) throws QueryParseException {
    GroupDescription.Basic g = groupResolver.parseId(maybeUUID);
    if (g == null || !groupControl.controlFor(g).isVisible()) {
      throw error("Group " + maybeUUID + " not found");
    }
    return g.getGroupUUID();
  }
}
