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

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;

import java.util.List;
import java.util.Set;

@Deprecated
class IsStarredByLegacyPredicate extends OrPredicate<ChangeData> {
  private static List<Predicate<ChangeData>> predicates(Set<Change.Id> ids) {
    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(ids.size());
    for (Change.Id id : ids) {
      r.add(new LegacyChangeIdPredicate(id));
    }
    return r;
  }

  private final Account.Id accountId;
  private final Set<Change.Id> starredChanges;

  IsStarredByLegacyPredicate(Account.Id accountId,
      Set<Change.Id> starredChanges) {
    super(predicates(starredChanges));
    this.accountId = accountId;
    this.starredChanges = starredChanges;
  }

  @Override
  public boolean match(final ChangeData object) {
    return starredChanges.contains(object.getId());
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_STARREDBY + ":" + accountId.toString();
  }
}
