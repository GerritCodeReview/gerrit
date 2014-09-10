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
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Predicate for a {@link Status}.
 * <p>
 * The actual name of this operator can differ, it usually comes as {@code
 * status:} but may also be {@code is:} to help do-what-i-meanery for end-users
 * searching for changes. Either operator name has the same meaning.
 * <p>
 * Status names are looked up by prefix case-insensitively.
 */
public final class ChangeStatusPredicate extends IndexPredicate<ChangeData> {
  private static final TreeMap<String, Change.Status> VALUES;

  static {
    VALUES = new TreeMap<>();
    for (Change.Status s : Change.Status.values()) {
      VALUES.put(canonicalize(s), s);
    }
  }

  public static String canonicalize(Change.Status status) {
    return status.name().toLowerCase();
  }

  public static Predicate<ChangeData> parse(String value) {
    if ("open".equalsIgnoreCase(value) || "pending".equalsIgnoreCase(value)) {
      return open();
    } else if ("closed".equalsIgnoreCase(value)) {
      return closed();
    }
    String lower = value.toLowerCase();
    NavigableMap<String, Change.Status> head = VALUES.tailMap(lower, true);
    if (!head.isEmpty()) {
      // Assume no statuses share a common prefix so we can only walk one entry.
      Map.Entry<String, Change.Status> e = head.entrySet().iterator().next();
      if (e.getKey().startsWith(lower)) {
        return new ChangeStatusPredicate(e.getValue());
      }
    }
    throw new IllegalArgumentException("invalid change status: " + value);
  }

  public static Predicate<ChangeData> open() {
    List<Predicate<ChangeData>> r = new ArrayList<>(4);
    for (final Change.Status e : Change.Status.values()) {
      if (e.isOpen()) {
        r.add(new ChangeStatusPredicate(e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  public static Predicate<ChangeData> closed() {
    List<Predicate<ChangeData>> r = new ArrayList<>(4);
    for (final Change.Status e : Change.Status.values()) {
      if (e.isClosed()) {
        r.add(new ChangeStatusPredicate(e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  private final Change.Status status;

  ChangeStatusPredicate(Change.Status status) {
    super(ChangeField.STATUS, canonicalize(status));
    this.status = status;
  }

  public Change.Status getStatus() {
    return status;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change change = object.change();
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
