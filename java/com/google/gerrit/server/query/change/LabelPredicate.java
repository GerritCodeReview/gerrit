// Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.index.query.OrPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.RangeUtil;
import com.google.gerrit.index.query.RangeUtil.Range;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class LabelPredicate extends OrPredicate<ChangeData> {
  protected static final int MAX_LABEL_VALUE = 4;
  protected static final int MAX_COUNT = 5; // inclusive

  protected static class Args {
    protected final Provider<InternalAccountQuery> queryProcessorProvider;
    protected final ProjectCache projectCache;
    protected final PermissionBackend permissionBackend;
    protected final IdentifiedUser.GenericFactory userFactory;
    protected final String value;
    protected final Set<Account.Id> accounts;
    protected final AccountGroup.UUID group;
    protected final Integer count;
    protected final PredicateArgs.Operator countOp;

    protected Args(
        Provider<InternalAccountQuery> queryProcessorProvider,
        ProjectCache projectCache,
        PermissionBackend permissionBackend,
        IdentifiedUser.GenericFactory userFactory,
        String value,
        Set<Account.Id> accounts,
        AccountGroup.UUID group,
        @Nullable Integer count,
        @Nullable PredicateArgs.Operator countOp) {
      this.queryProcessorProvider = queryProcessorProvider;
      this.projectCache = projectCache;
      this.permissionBackend = permissionBackend;
      this.userFactory = userFactory;
      this.value = value;
      this.accounts = accounts;
      this.group = group;
      this.count = count;
      this.countOp = countOp;
    }
  }

  protected static class Parsed {
    protected final String label;
    protected final String test;
    protected final int numericValue;

    protected Parsed(String label, String test, int numericValue) {
      this.label = label;
      this.test = test;
      this.numericValue = numericValue;
    }
  }

  protected final String value;

  public LabelPredicate(
      ChangeQueryBuilder.Arguments a,
      String value,
      Set<Account.Id> accounts,
      AccountGroup.UUID group,
      @Nullable Integer count,
      @Nullable PredicateArgs.Operator countOp) {
    super(
        predicates(
            new Args(
                a.accountQueryProvider,
                a.projectCache,
                a.permissionBackend,
                a.userFactory,
                value,
                accounts,
                group,
                count,
                countOp)));
    this.value = value;
  }

  protected static List<Predicate<ChangeData>> predicates(Args args) {
    String v = args.value;
    List<Integer> counts = getCounts(args.count, args.countOp);
    try {
      MagicLabelVote mlv = MagicLabelVote.parseWithEquals(v);
      List<Predicate<ChangeData>> result = Lists.newArrayListWithCapacity(counts.size());
      if (counts.isEmpty()) {
        result.add(magicLabelPredicate(args, mlv, /* count= */ null));
      } else {
        counts.forEach(count -> result.add(magicLabelPredicate(args, mlv, count)));
      }
      return result;
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    Parsed parsed = null;

    try {
      LabelVote lv = LabelVote.parse(v);
      parsed = new Parsed(lv.label(), "=", lv.value());
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    try {
      LabelVote lv = LabelVote.parseWithEquals(v);
      parsed = new Parsed(lv.label(), "=", lv.value());
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    Range range;
    if (parsed == null) {
      range = RangeUtil.getRange(v, -MAX_LABEL_VALUE, MAX_LABEL_VALUE);
      if (range == null) {
        range = new Range(v, 1, 1);
      }
    } else {
      range =
          RangeUtil.getRange(
              parsed.label, parsed.test, parsed.numericValue, -MAX_LABEL_VALUE, MAX_LABEL_VALUE);
    }
    String prefix = range.prefix;
    int min = range.min;
    int max = range.max;

    List<Predicate<ChangeData>> r =
        Lists.newArrayListWithCapacity((counts.isEmpty() ? 1 : counts.size()) * (max - min + 1));
    for (int i = min; i <= max; i++) {
      if (counts.isEmpty()) {
        r.add(onePredicate(args, prefix, i, /* count= */ null));
      } else {
        for (int count : counts) {
          r.add(onePredicate(args, prefix, i, count));
        }
      }
    }
    return r;
  }

  protected static Predicate<ChangeData> onePredicate(
      Args args, String label, int expVal, @Nullable Integer count) {
    if (expVal != 0) {
      return equalsLabelPredicate(args, label, expVal, count);
    }
    return noLabelQuery(args, label);
  }

  protected static Predicate<ChangeData> noLabelQuery(Args args, String label) {
    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    for (int i = 1; i <= MAX_LABEL_VALUE; i++) {
      r.add(equalsLabelPredicate(args, label, i, /* count= */ null));
      r.add(equalsLabelPredicate(args, label, -i, /* count= */ null));
    }
    return not(or(r));
  }

  protected static Predicate<ChangeData> equalsLabelPredicate(
      Args args, String label, int expVal, @Nullable Integer count) {
    if (args.accounts == null || args.accounts.isEmpty()) {
      return new EqualsLabelPredicate(args, label, expVal, null, count);
    }
    List<Predicate<ChangeData>> r = new ArrayList<>();
    for (Account.Id a : args.accounts) {
      r.add(new EqualsLabelPredicate(args, label, expVal, a, count));
    }
    return or(r);
  }

  protected static Predicate<ChangeData> magicLabelPredicate(
      Args args, MagicLabelVote mlv, @Nullable Integer count) {
    if (args.accounts == null || args.accounts.isEmpty()) {
      return new MagicLabelPredicate(args, mlv, /* account= */ null, count);
    }
    List<Predicate<ChangeData>> r = new ArrayList<>();
    for (Account.Id a : args.accounts) {
      r.add(new MagicLabelPredicate(args, mlv, a, count));
    }
    return or(r);
  }

  private static List<Integer> getCounts(
      @Nullable Integer count, @Nullable PredicateArgs.Operator countOp) {
    List<Integer> result = new ArrayList<>();
    if (count == null) {
      return result;
    }
    switch (countOp) {
      case EQUAL:
      case GREATER_EQUAL:
      case LESS_EQUAL:
        result.add(count);
        break;
      case GREATER:
      case LESS:
      default:
        break;
    }
    switch (countOp) {
      case GREATER:
      case GREATER_EQUAL:
        IntStream.range(count + 1, MAX_COUNT + 1).forEach(result::add);
        break;
      case LESS:
      case LESS_EQUAL:
        IntStream.range(0, count).forEach(result::add);
        break;
      case EQUAL:
      default:
        break;
    }
    return result;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_LABEL + ":" + value;
  }
}
