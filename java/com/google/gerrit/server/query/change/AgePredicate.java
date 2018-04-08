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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.ConfigUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;
import java.sql.Timestamp;

public class AgePredicate extends TimestampRangeChangePredicate {
  protected final long cut;

  public AgePredicate(String value) {
    super(ChangeField.UPDATED, ChangeQueryBuilder.FIELD_AGE, value);

    long s = ConfigUtil.getTimeUnit(getValue(), 0, SECONDS);
    long ms = MILLISECONDS.convert(s, SECONDS);
    this.cut = TimeUtil.nowMs() - ms;
  }

  @Override
  public Timestamp getMinTimestamp() {
    return new Timestamp(0);
  }

  @Override
  public Timestamp getMaxTimestamp() {
    return new Timestamp(cut);
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change change = object.change();
    return change != null && change.getLastUpdatedOn().getTime() <= cut;
  }
}
