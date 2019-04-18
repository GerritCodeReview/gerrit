// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.Date;

public class AfterPredicate extends TimestampRangeChangePredicate {
  protected final Date cut;

  public AfterPredicate(String value) throws QueryParseException {
    super(ChangeField.UPDATED, ChangeQueryBuilder.FIELD_BEFORE, value);
    cut = parse(value);
  }

  @Override
  public Date getMinTimestamp() {
    return cut;
  }

  @Override
  public Date getMaxTimestamp() {
    return new Date(Long.MAX_VALUE);
  }

  @Override
  public boolean match(ChangeData cd) {
    return cd.change().getLastUpdatedOn().getTime() >= cut.getTime();
  }
}
