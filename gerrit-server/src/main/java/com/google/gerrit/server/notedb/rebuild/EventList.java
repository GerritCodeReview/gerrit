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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

class EventList<E extends Event> implements Iterable<E> {
  private final ArrayList<E> list = new ArrayList<>();
  private boolean isSubmit;

  @Override
  public Iterator<E> iterator() {
    return list.iterator();
  }

  void add(E e) {
    list.add(e);
    if (e.isSubmit()) {
      isSubmit = true;
    }
  }

  void clear() {
    list.clear();
    isSubmit = false;
  }

  boolean isEmpty() {
    return list.isEmpty();
  }

  boolean canAdd(E e) {
    if (isEmpty()) {
      return true;
    }
    if (e instanceof FinalUpdatesEvent) {
      return false; // FinalUpdatesEvent always gets its own update.
    }

    Event last = getLast();
    if (!Objects.equals(e.user, last.user)
        || !Objects.equals(e.realUser, last.realUser)
        || !e.psId.equals(last.psId)
        || !Objects.equals(e.tag, last.tag)) {
      return false; // Different patch set, author, or tag.
    }
    if (e.isPostSubmitApproval() && isSubmit) {
      // Post-submit approvals must come after the update that submits.
      return false;
    }

    long t = e.when.getTime();
    long tFirst = getFirstTime();
    long tLast = getLastTime();
    checkArgument(t >= tLast, "event %s is before previous event in list %s", e, last);
    if (t - tLast > ChangeRebuilderImpl.MAX_DELTA_MS
        || t - tFirst > ChangeRebuilderImpl.MAX_WINDOW_MS) {
      return false; // Too much time elapsed.
    }

    if (!e.uniquePerUpdate()) {
      return true;
    }
    for (Event o : this) {
      if (e.getClass() == o.getClass()) {
        return false; // Only one event of this type allowed per update.
      }
    }

    // TODO(dborowitz): Additional heuristics, like keeping events separate if
    // they affect overlapping fields within a single entity.

    return true;
  }

  Timestamp getWhen() {
    return get(0).when;
  }

  PatchSet.Id getPatchSetId() {
    PatchSet.Id id = checkNotNull(get(0).psId);
    for (int i = 1; i < size(); i++) {
      checkState(
          get(i).psId.equals(id), "mismatched patch sets in EventList: %s != %s", id, get(i).psId);
    }
    return id;
  }

  Account.Id getAccountId() {
    Account.Id id = get(0).user;
    for (int i = 1; i < size(); i++) {
      checkState(
          Objects.equals(id, get(i).user),
          "mismatched users in EventList: %s != %s",
          id,
          get(i).user);
    }
    return id;
  }

  Account.Id getRealAccountId() {
    Account.Id id = get(0).realUser;
    for (int i = 1; i < size(); i++) {
      checkState(
          Objects.equals(id, get(i).realUser),
          "mismatched real users in EventList: %s != %s",
          id,
          get(i).realUser);
    }
    return id;
  }

  String getTag() {
    return getLast().tag;
  }

  private E get(int i) {
    return list.get(i);
  }

  private int size() {
    return list.size();
  }

  private E getLast() {
    return list.get(list.size() - 1);
  }

  private long getLastTime() {
    return getLast().when.getTime();
  }

  private long getFirstTime() {
    return list.get(0).when.getTime();
  }
}
