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

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.server.index.change.ChangeField;

public class SubmittablePredicate extends ChangeIndexPredicate {
  protected final SubmitRecord.Status status;

  public SubmittablePredicate(SubmitRecord.Status status) {
    super(ChangeField.SUBMIT_RECORD, status.name());
    this.status = status;
  }

  @Override
  public boolean match(ChangeData cd) {
    return cd.submitRecords(ChangeField.SUBMIT_RULE_OPTIONS_STRICT).stream()
        .anyMatch(r -> r.status == status);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
