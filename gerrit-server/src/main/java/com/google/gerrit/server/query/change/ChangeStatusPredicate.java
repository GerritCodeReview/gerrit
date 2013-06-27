// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableBiMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicate for a {@link Change.Status}.
 * <p>
 * The actual name of this operator can differ, it usually comes as {@code
 * status:} but may also be {@code is:} to help do-what-i-meanery for end-users
 * searching for changes. Either operator name has the same meaning.
 */
public final class ChangeStatusPredicate extends IndexPredicate<ChangeData> {
  public static final ImmutableBiMap<Change.Status, String> VALUES;

  static {
    ImmutableBiMap.Builder<Change.Status, String> values =
        ImmutableBiMap.builder();
    for (Change.Status s : Change.Status.values()) {
      values.put(s, s.name().toLowerCase());
    }
    VALUES = values.build();
  }

  public static Predicate<ChangeData> open(Provider<ReviewDb> dbProvider) {
    List<Predicate<ChangeData>> r = new ArrayList<Predicate<ChangeData>>(4);
    for (final Change.Status e : Change.Status.values()) {
      if (e.isOpen()) {
        r.add(new ChangeStatusPredicate(dbProvider, e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  public static Predicate<ChangeData> closed(Provider<ReviewDb> dbProvider) {
    List<Predicate<ChangeData>> r = new ArrayList<Predicate<ChangeData>>(4);
    for (final Change.Status e : Change.Status.values()) {
      if (e.isClosed()) {
        r.add(new ChangeStatusPredicate(dbProvider, e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  private final Provider<ReviewDb> dbProvider;
  private final Change.Status status;

  ChangeStatusPredicate(Provider<ReviewDb> dbProvider, String value) {
    super(ChangeField.STATUS, value);
    this.dbProvider = dbProvider;
    status = VALUES.inverse().get(value);
    checkArgument(status != null, "invalid change status: %s", value);
  }

  ChangeStatusPredicate(Provider<ReviewDb> dbProvider, Change.Status status) {
    super(ChangeField.STATUS, VALUES.get(status));
    this.dbProvider = dbProvider;
    this.status = status;
  }

  public Change.Status getStatus() {
    return status;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change(dbProvider);
    return change != null && status.equals(change.getStatus());
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public int hashCode() {
    return status.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof ChangeStatusPredicate) {
      final ChangeStatusPredicate p = (ChangeStatusPredicate) other;
      return status.equals(p.status);
    }
    return false;
  }

  @Override
  public String toString() {
    return getOperator() + ":" + getValue();
  }
}
