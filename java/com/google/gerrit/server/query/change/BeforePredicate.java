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

import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.query.QueryParseException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Predicate that matches a {@link Timestamp} field from the index in a range from the the epoch to
 * the passed {@code String} representation of the Timestamp value.
 */
public class BeforePredicate extends TimestampRangeChangePredicate {
  protected final Instant cut;

  public BeforePredicate(FieldDef<ChangeData, Timestamp> def, String name, String value)
      throws QueryParseException {
    super(def, name, value);
    cut = parse(value);
  }

  @Override
  public Instant getMinTimestamp() {
    return Instant.ofEpochMilli(0);
  }

  @Override
  public Instant getMaxTimestamp() {
    return cut;
  }

  @Override
  public boolean match(ChangeData cd) {
    Timestamp valueTimestamp = this.getValueTimestamp(cd);
    if (valueTimestamp == null) {
      return false;
    }
    return valueTimestamp.getTime() <= cut.toEpochMilli();
  }
}
