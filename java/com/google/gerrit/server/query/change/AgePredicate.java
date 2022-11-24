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

import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.time.Instant;

public class AgePredicate extends TimestampRangeChangePredicate {
  protected final Instant cut;

  public AgePredicate(String value) {
    super(ChangeField.UPDATED_SPEC, ChangeQueryBuilder.FIELD_AGE, value);

    long s = ConfigUtil.getTimeUnit(getValue(), 0, SECONDS);
    long ms = MILLISECONDS.convert(s, SECONDS);
    this.cut = Instant.ofEpochMilli(TimeUtil.nowMs() - ms);
  }

  @Override
  public Instant getMinTimestamp() {
    return Instant.EPOCH;
  }

  @Override
  public Instant getMaxTimestamp() {
    return cut;
  }

  @Override
  public boolean match(ChangeData object) {
    Timestamp valueTimestamp = this.getValueTimestamp(object);
    if (valueTimestamp == null) {
      return false;
    }
    return valueTimestamp.getTime() <= cut.toEpochMilli();
  }
}
