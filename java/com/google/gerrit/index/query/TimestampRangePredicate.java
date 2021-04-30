// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.index.query;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.json.JavaSqlTimestampHelper;
import java.sql.Timestamp;
import java.util.Date;

// TODO: Migrate this to IntegerRangePredicate
public abstract class TimestampRangePredicate<I> extends IndexPredicate<I> {
  protected static Timestamp parse(String value) throws QueryParseException {
    try {
      return JavaSqlTimestampHelper.parseTimestamp(value);
    } catch (IllegalArgumentException e) {
      // parseTimestamp's errors are specific and helpful, so preserve them.
      throw new QueryParseException(e.getMessage(), e);
    }
  }

  protected TimestampRangePredicate(FieldDef<I, Timestamp> def, String name, String value) {
    super(def, name, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean match(I object) {
    checkState(getField().getType().getName().equals(FieldType.TIMESTAMP.getName()));
    Timestamp timestamp = (Timestamp) getField().get(object);
    if (timestamp == null) {
      return false;
    }
    return timestamp.getTime() >= getMinTimestamp().getTime()
        && timestamp.getTime() <= getMaxTimestamp().getTime();
  }

  protected Timestamp getValueTimestamp(I object) {
    return (Timestamp) this.getField().get(object);
  }

  public abstract Date getMinTimestamp();

  public abstract Date getMaxTimestamp();
}
