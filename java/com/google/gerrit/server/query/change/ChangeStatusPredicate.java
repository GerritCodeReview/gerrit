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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
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
  private static final String INVALID_STATUS = "__invalid__";
  static final Predicate<ChangeData> NONE = new ChangeStatusPredicate(null);

  private static final TreeMap<String, Predicate<ChangeData>> PREDICATES;
  private static final Predicate<ChangeData> CLOSED;
  private static final Predicate<ChangeData> OPEN;

  static {
    PREDICATES = new TreeMap<>();
    List<Predicate<ChangeData>> open = new ArrayList<>();
    List<Predicate<ChangeData>> closed = new ArrayList<>();

    for (Change.Status s : Change.Status.values()) {
      ChangeStatusPredicate p = forStatus(s);
      String str = canonicalize(s);
      checkState(
          !INVALID_STATUS.equals(str),
          "invalid status sentinel %s cannot match canonicalized status string %s",
          INVALID_STATUS,
          str);
      PREDICATES.put(str, p);
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

  public static Predicate<ChangeData> parse(String value) {
    String lower = value.toLowerCase();
    NavigableMap<String, Predicate<ChangeData>> head = PREDICATES.tailMap(lower, true);
    if (!head.isEmpty()) {
      // Assume no statuses share a common prefix so we can only walk one entry.
      Map.Entry<String, Predicate<ChangeData>> e = head.entrySet().iterator().next();
      if (e.getKey().startsWith(lower)) {
        return e.getValue();
      }
    }
    return NONE;
  }

  public static Predicate<ChangeData> open() {
    return OPEN;
  }

  public static Predicate<ChangeData> closed() {
    return CLOSED;
  }

  public static ChangeStatusPredicate forStatus(Change.Status status) {
    return new ChangeStatusPredicate(requireNonNull(status));
  }

  @Nullable private final Change.Status status;

  private ChangeStatusPredicate(@Nullable Change.Status status) {
    super(ChangeField.STATUS, status != null ? canonicalize(status) : INVALID_STATUS);
    this.status = status;
  }

  /**
   * Get the status for this predicate.
   *
   * @return the status, or null if this predicate is intended to never match any changes.
   */
  @Nullable
  public Change.Status getStatus() {
    return status;
  }

  @Override
  public boolean match(ChangeData object) throws StorageException {
    Change change = object.change();
    return change != null && Objects.equals(status, change.getStatus());
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(status);
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof ChangeStatusPredicate)
        && Objects.equals(status, ((ChangeStatusPredicate) other).status);
  }

  @Override
  public String toString() {
    return getOperator() + ":" + getValue();
  }
}
