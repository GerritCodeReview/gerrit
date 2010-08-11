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

import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData.NeededData;

import java.util.Collection;
import java.util.EnumSet;

public class PrefetchableOrPredicate extends OrPredicate<ChangeData> implements
    Prefetchable {
  public PrefetchableOrPredicate(Predicate<ChangeData>... that) {
    super(that);
  }

  public PrefetchableOrPredicate(
      Collection<? extends Predicate<ChangeData>> that) {
    super(that);
  }

  @Override
  public EnumSet<NeededData> getNeededData() {
    EnumSet<NeededData> needed = EnumSet.noneOf(NeededData.class);
    for (Predicate<ChangeData> p : getChildren()) {
      if (p instanceof Prefetchable) {
        needed.addAll(((Prefetchable) p).getNeededData());
      }
    }
    return needed;
  }
}
