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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Change.Status;
import com.google.gerrit.index.query.HasCardinality;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public final class ChangeStatusPredicate extends ChangeIndexPredicate implements HasCardinality {
  private static final String INVALID_STATUS = "__invalid__";
  static final Predicate<ChangeData> NONE = new ChangeStatusPredicate(null);

  private static final TreeMap<String, Provider<Predicate<ChangeData>>> PREDICATES;
  private static final Provider<Predicate<ChangeData>> CLOSED;
  private static final Provider<Predicate<ChangeData>> OPEN;

  static {
    PREDICATES = new TreeMap<>();
    List<Change.Status> openStatuses = new ArrayList<>();
    List<Change.Status> closedStatuses = new ArrayList<>();

    for (Change.Status s : Change.Status.values()) {
      String str = canonicalize(s);
      checkState(
          !INVALID_STATUS.equals(str),
          "invalid status sentinel %s cannot match canonicalized status string %s",
          INVALID_STATUS,
          str);
      PREDICATES.put(str, () -> forStatus(s));
      (s.isOpen() ? openStatuses : closedStatuses).add(s);
    }

    CLOSED = () -> statusesToPrecidate(closedStatuses);
    OPEN = () -> statusesToPrecidate(openStatuses);

    PREDICATES.put("closed", CLOSED);
    PREDICATES.put("open", OPEN);
    PREDICATES.put("pending", OPEN);
  }

  private static Predicate<ChangeData> statusesToPrecidate(List<Change.Status> statuses) {
    return Predicate.or(
        statuses.stream().map(ChangeStatusPredicate::forStatus).collect(toImmutableList()));
  }

  public static String canonicalize(Change.Status status) {
    return status.name().toLowerCase(Locale.US);
  }

  public static Predicate<ChangeData> parse(String value) throws QueryParseException {
    String lower = value.toLowerCase(Locale.US);
    NavigableMap<String, Provider<Predicate<ChangeData>>> head = PREDICATES.tailMap(lower, true);
    if (!head.isEmpty()) {
      // Assume no statuses share a common prefix so we can only walk one entry.
      Map.Entry<String, Provider<Predicate<ChangeData>>> e = head.entrySet().iterator().next();
      if (e.getKey().startsWith(lower)) {
        return e.getValue().get();
      }
    }
    throw new QueryParseException("Unrecognized value: " + value);
  }

  public static Predicate<ChangeData> open() {
    return OPEN.get();
  }

  public static Predicate<ChangeData> closed() {
    return CLOSED.get();
  }

  public static ChangeStatusPredicate forStatus(Change.Status status) {
    return new ChangeStatusPredicate(requireNonNull(status));
  }

  @Nullable private final Change.Status status;

  private ChangeStatusPredicate(@Nullable Change.Status status) {
    super(ChangeField.STATUS_SPEC, status != null ? canonicalize(status) : INVALID_STATUS);
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
  public boolean match(ChangeData object) {
    Change change = object.change();
    return change != null && Objects.equals(status, change.getStatus());
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

  @Override
  public int getCardinality() {
    if (getStatus() == null) {
      return 0;
    }
    switch (getStatus()) {
      case MERGED:
        return 50_000;
      case ABANDONED:
        return 50_000;
      case NEW:
      default:
        return 2000;
    }
  }
}
