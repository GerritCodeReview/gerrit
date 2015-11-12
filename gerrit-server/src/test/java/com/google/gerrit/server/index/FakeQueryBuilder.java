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

package com.google.gerrit.server.index;

import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtorm.server.OrmException;

public class FakeQueryBuilder extends ChangeQueryBuilder {
  FakeQueryBuilder(IndexCollection indexes) {
    super(
        new FakeQueryBuilder.Definition<>(
          FakeQueryBuilder.class),
        new ChangeQueryBuilder.Arguments(null, null, null, null,
          null, null, null, null, null, null, null, null, null,
          null, null, null, null, null, null, indexes, null, null,
          null, null, null));
  }

  @Operator
  public Predicate<ChangeData> foo(String value) {
    return predicate("foo", value);
  }

  @Operator
  public Predicate<ChangeData> bar(String value) {
    return predicate("bar", value);
  }

  private Predicate<ChangeData> predicate(String name, String value) {
    return new OperatorPredicate<ChangeData>(name, value) {
      @Override
      public boolean match(ChangeData object) throws OrmException {
        return false;
      }

      @Override
      public int getCost() {
        return 0;
      }
    };
  }
}
