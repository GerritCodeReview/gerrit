// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.change.ChangeField;

public class SubmissionIdPredicate extends ChangeIndexPredicate {
  public SubmissionIdPredicate(String changeSet) {
    super(ChangeField.SUBMISSIONID, changeSet);
  }

  @Override
  public boolean match(ChangeData object) throws StorageException {
    Change change = object.change();
    if (change == null) {
      return false;
    }
    if (change.getSubmissionId() == null) {
      return false;
    }
    return getValue().equals(change.getSubmissionId());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
