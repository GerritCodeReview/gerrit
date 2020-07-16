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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.Set;

public class SubmitRecordPredicate extends ChangeIndexPredicate {
  public static Predicate<ChangeData> create(
      String label, SubmitRecord.Label.Status status, Set<Account.Id> accounts) {
    String lowerLabel = label.toLowerCase();
    if (accounts == null || accounts.isEmpty()) {
      return new SubmitRecordPredicate(status.name() + ',' + lowerLabel);
    }
    return Predicate.or(
        accounts.stream()
            .map(a -> new SubmitRecordPredicate(status.name() + ',' + lowerLabel + ',' + a.get()))
            .collect(toList()));
  }

  private SubmitRecordPredicate(String value) {
    super(ChangeField.SUBMIT_RECORD, value);
  }

  @Override
  public boolean match(ChangeData in) {
    return ChangeField.formatSubmitRecordValues(in).contains(getValue());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
