// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.client.OrmException;

import org.apache.lucene.search.Query;

import java.util.Collection;

class LucenePredicate extends Predicate<ChangeData> {
  private final Query query;

  LucenePredicate(Query query) {
    this.query = query;
  }

  Query getQuery() {
    return query;
  }

  @Override
  public Predicate<ChangeData> copy(
      Collection<? extends Predicate<ChangeData>> children) {
    return new LucenePredicate(query);
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    throw new OrmException("not supported");
  }

  @Override
  public String toString() {
    return query.toString();
  }
}
