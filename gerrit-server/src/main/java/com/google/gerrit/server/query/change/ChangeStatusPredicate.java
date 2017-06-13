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
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Predicate for a {@link Status}.
 *
 * <p>The actual name of this operator can differ, it usually comes as {@code status:} but may also
 * be {@code is:} to help do-what-i-meanery for end-users searching for changes. Either operator
 * name has the same meaning.
 *
 * <p>Status names are looked up by prefix case-insensitively.
 */
public final class ChangeStatusPredicate extends ChangeIndexPredicate {
  protected static final TreeMap<String, Predicate<ChangeData>> PREDICATES;
  protected static final Predicate<ChangeData> CLOSED;
  protected static final Predicate<ChangeData> OPEN;

  static {
    PREDICATES = new TreeMap<>();
    List<Predicate<ChangeData>> open = new ArrayList<>();
    List<Predicate<ChangeData>> closed = new ArrayList<>();

    for (Change.Status s : Change.Status.values()) {
      ChangeStatusPredicate p = new ChangeStatusPredicate(s);
      PREDICATES.put(canonicalize(s), p);
      (s.isOpen() ? open : closed).add(p);
    }

    CLOSED = Predicate.or(closed);
    OPEN = Predicate.or(open);

    PREDICATES.put("closed", CLOSED);
    PREDICATES.put("open", OPEN);
    PREDICATES.put("pending", OPEN);
  }

  public static String canonicalize(Change.Status status) {
    return status.name().toLowerCase();
  }

  public static Predicate<ChangeData> parse(String value) throws QueryParseException {
    String lower = value.toLowerCase();
    NavigableMap<String, Predicate<ChangeData>> head = PREDICATES.tailMap(lower, true);
    if (!head.isEmpty()) {
      // Assume no statuses share a common prefix so we can only walk one entry.
      Map.Entry<String, Predicate<ChangeData>> e = head.entrySet().iterator().next();
      if (e.getKey().startsWith(lower)) {
        return e.getValue();
      }
    }
    throw new QueryParseException("invalid change status: " + value);
  }

  public static Predicate<ChangeData> open() {
    return OPEN;
  }

  public static Predicate<ChangeData> closed() {
    return CLOSED;
  }

  protected final Change.Status status;

  public ChangeStatusPredicate(Change.Status status) {
    super(ChangeField.STATUS, canonicalize(status));
    this.status = status;
  }

  public Change.Status getStatus() {
    return status;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
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
