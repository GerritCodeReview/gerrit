// Copyright (C) 2016 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import java.util.Set;

public class SubmitRecordPredicate extends ChangeIndexPredicate {
  public static Predicate<ChangeData> create(
      Arguments args, String label, SubmitRecord.Label.Status status, Set<Account.Id> accounts) {
    String lowerLabel = label.toLowerCase();
    if (accounts == null || accounts.isEmpty()) {
      return new SubmitRecordPredicate(args, status.name() + ',' + lowerLabel);
    }
    return Predicate.or(
        accounts
            .stream()
            .map(
                a ->
                    new SubmitRecordPredicate(
                        args, status.name() + ',' + lowerLabel + ',' + a.get()))
            .collect(toList()));
  }

  private final Arguments args;

  private SubmitRecordPredicate(Arguments args, String value) {
    super(ChangeField.SUBMIT_RECORD, value);
    this.args = args;
  }

  @Override
  public boolean match(ChangeData in) throws OrmException {
    return ChangeField.formatSubmitRecordValues(args.accounts, in).contains(getValue());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
