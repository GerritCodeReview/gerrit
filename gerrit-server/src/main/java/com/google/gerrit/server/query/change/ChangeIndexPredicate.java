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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeFillArgs;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.Matchable;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;

public abstract class ChangeIndexPredicate extends IndexPredicate<ChangeData, ChangeFillArgs>
    implements Matchable<ChangeData> {
  protected ChangeIndexPredicate(FieldDef<ChangeData, ChangeFillArgs, ?> def, String value) {
    super(def, value);
  }

  protected ChangeIndexPredicate(
      FieldDef<ChangeData, ChangeFillArgs, ?> def, String name, String value) {
    super(def, name, value);
  }

  protected static Predicate<ChangeData> create(Arguments args, Predicate<ChangeData> p) {
    if (!args.allowsDrafts) {
      return Predicate.and(p, Predicate.not(new ChangeStatusPredicate(Change.Status.DRAFT)));
    }
    return p;
  }
}
