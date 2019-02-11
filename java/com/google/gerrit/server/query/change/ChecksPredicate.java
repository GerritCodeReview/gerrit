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

import com.google.gerrit.extensions.common.CombinedCheckState;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;

public class ChecksPredicate extends ChangeIndexPredicate {
  private final CombinedCheckState state;

  ChecksPredicate(String value) throws QueryParseException {
    super(
        ChangeField.COMBINED_CHECK_STATE,
        CombinedCheckState.tryParse(value)
            .orElseThrow(() -> new QueryParseException("Invalid combined check state: " + value))
            .toIndexString());
    this.state = CombinedCheckState.parse(value);
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return state.equals(cd.combinedCheckState());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
