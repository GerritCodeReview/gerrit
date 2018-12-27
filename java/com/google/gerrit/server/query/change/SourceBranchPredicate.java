// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;

public class SourceBranchPredicate extends ChangeIndexPredicate {
  public SourceBranchPredicate(String ref) {
    super(ChangeField.SOURCE_REF, ref);
    checkArgument(ref.startsWith(R_HEADS), "full ref name required: %s", ref);
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change change = object.change();
    if (change == null || change.getSource() == null) {
      return false;
    }
    return getValue().equals(change.getSource().get());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
