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

import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IntegerRangePredicate;
import com.google.gerrit.server.query.Matchable;
import com.google.gerrit.server.query.QueryParseException;

public abstract class IntegerRangeChangePredicate extends IntegerRangePredicate<ChangeData>
    implements Matchable<ChangeData> {

  protected IntegerRangeChangePredicate(FieldDef<ChangeData, Integer> type, String value)
      throws QueryParseException {
    super(type, value);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
