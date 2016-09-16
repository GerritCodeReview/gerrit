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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl.MAX_WINDOW_MS;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.notedb.AbstractChangeUpdate;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

abstract class Event implements Comparable<Event> {
  // NOTE: EventList only supports direct subclasses, not an arbitrary
  // hierarchy.

  final Account.Id who;
  final String tag;
  final boolean predatesChange;
  final List<Event> deps;
  Timestamp when;
  PatchSet.Id psId;

  protected Event(PatchSet.Id psId, Account.Id who, Timestamp when,
      Timestamp changeCreatedOn, String tag) {
    this.psId = psId;
    this.who = who;
    this.tag = tag;
    // Truncate timestamps at the change's createdOn timestamp.
    predatesChange = when.before(changeCreatedOn);
    this.when = predatesChange ? changeCreatedOn : when;
    deps = new ArrayList<>();
  }

  protected void checkUpdate(AbstractChangeUpdate update) {
    checkState(Objects.equals(update.getPatchSetId(), psId),
        "cannot apply event for %s to update for %s",
        update.getPatchSetId(), psId);
    checkState(when.getTime() - update.getWhen().getTime() <= MAX_WINDOW_MS,
        "event at %s outside update window starting at %s",
        when, update.getWhen());
    checkState(Objects.equals(update.getNullableAccountId(), who),
        "cannot apply event by %s to update by %s",
        who, update.getNullableAccountId());
  }

  void addDep(Event e) {
    deps.add(e);
  }

  /**
   * @return whether this event type must be unique per {@link ChangeUpdate},
   *     i.e. there may be at most one of this type.
   */
  abstract boolean uniquePerUpdate();

  abstract void apply(ChangeUpdate update) throws OrmException, IOException;

  protected boolean isPatchSet() {
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("psId", psId)
        .add("who", who)
        .add("when", when)
        .toString();
  }

  @Override
  public int compareTo(Event other) {
    return ComparisonChain.start()
        .compare(this.when, other.when)
        .compareTrueFirst(isPatchSet(), isPatchSet())
        .compareTrueFirst(this.predatesChange, other.predatesChange)
        .compare(this.who, other.who, ReviewDbUtil.intKeyOrdering())
        .compare(this.psId, other.psId,
            ReviewDbUtil.intKeyOrdering().nullsLast())
        .result();
  }
}
