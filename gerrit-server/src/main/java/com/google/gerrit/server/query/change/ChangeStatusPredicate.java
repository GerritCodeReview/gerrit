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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Predicate for a {@link Change.Status}.
 * <p>
 * The actual name of this operator can differ, it usually comes as {@code
 * status:} but may also be {@code is:} to help do-what-i-meanery for end-users
 * searching for changes. Either operator name has the same meaning.
 */
final class ChangeStatusPredicate extends OperatorPredicate<ChangeData, PatchSet> {
  private static final Map<String, Change.Status> byName;
  private static final EnumMap<Change.Status, String> byEnum;

  static {
    byName = new HashMap<String, Change.Status>();
    byEnum = new EnumMap<Change.Status, String>(Change.Status.class);
    for (final Change.Status s : Change.Status.values()) {
      final String name = s.name().toLowerCase();
      byName.put(name, s);
      byEnum.put(s, name);
    }
  }

  static Predicate<ChangeData, PatchSet> open(Provider<ReviewDb> dbProvider) {
    List<Predicate<ChangeData, PatchSet>> r = new ArrayList<Predicate<ChangeData, PatchSet>>(4);
    for (final Change.Status e : Change.Status.values()) {
      if (e.isOpen()) {
        r.add(new ChangeStatusPredicate(dbProvider, e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  static Predicate<ChangeData, PatchSet> closed(Provider<ReviewDb> dbProvider) {
    List<Predicate<ChangeData, PatchSet>> r = new ArrayList<Predicate<ChangeData, PatchSet>>(4);
    for (final Change.Status e : Change.Status.values()) {
      if (e.isClosed()) {
        r.add(new ChangeStatusPredicate(dbProvider, e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  private static Change.Status parse(final String value) {
    final Change.Status s = byName.get(value);
    if (s == null) {
      throw new IllegalArgumentException();
    }
    return s;
  }

  private final Provider<ReviewDb> dbProvider;
  private final Change.Status status;

  ChangeStatusPredicate(Provider<ReviewDb> dbProvider, String value) {
    this(dbProvider, parse(value));
  }

  ChangeStatusPredicate(Provider<ReviewDb> dbProvider, Change.Status status) {
    super(ChangeQueryBuilder.FIELD_STATUS, byEnum.get(status));
    this.dbProvider = dbProvider;
    this.status = status;
  }

  Change.Status getStatus() {
    return status;
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet subobject)
      throws OrmException {
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
