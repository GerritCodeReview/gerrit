// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A set of changes grouped together to be submitted atomically.
 *
 * <p>MergeSuperSet constructs ChangeSets to accumulate intermediate results toward the ChangeSet it
 * returns when done.
 *
 * <p>This class is not thread safe.
 */
public class ChangeSet {
  private final ImmutableMap<Change.Id, ChangeData> changeData;

  /**
   * Additional changes not included in changeData because their connection to the original change
   * is not visible to the current user. That is, this map includes both - changes that are not
   * visible to the current user, and - changes whose only relationship to the set is via a change
   * that is not visible to the current user
   */
  private final ImmutableMap<Change.Id, ChangeData> nonVisibleChanges;

  private static ImmutableMap<Change.Id, ChangeData> index(
      Iterable<ChangeData> changes, Collection<Change.Id> exclude) {
    Map<Change.Id, ChangeData> ret = new LinkedHashMap<>();
    for (ChangeData cd : changes) {
      Change.Id id = cd.getId();
      if (!ret.containsKey(id) && !exclude.contains(id)) {
        ret.put(id, cd);
      }
    }
    return ImmutableMap.copyOf(ret);
  }

  public ChangeSet(Iterable<ChangeData> changes, Iterable<ChangeData> hiddenChanges) {
    changeData = index(changes, ImmutableList.<Change.Id>of());
    nonVisibleChanges = index(hiddenChanges, changeData.keySet());
  }

  public ChangeSet(ChangeData change, boolean visible) {
    this(
        visible ? ImmutableList.of(change) : ImmutableList.<ChangeData>of(),
        ImmutableList.of(change));
  }

  public ImmutableSet<Change.Id> ids() {
    return changeData.keySet();
  }

  public ImmutableMap<Change.Id, ChangeData> changesById() {
    return changeData;
  }

  public Multimap<Branch.NameKey, ChangeData> changesByBranch() throws OrmException {
    ListMultimap<Branch.NameKey, ChangeData> ret = ArrayListMultimap.create();
    for (ChangeData cd : changeData.values()) {
      ret.put(cd.change().getDest(), cd);
    }
    return ret;
  }

  public ImmutableCollection<ChangeData> changes() {
    return changeData.values();
  }

  public ImmutableSet<Change.Id> nonVisibleIds() {
    return nonVisibleChanges.keySet();
  }

  public ImmutableList<ChangeData> nonVisibleChanges() {
    return nonVisibleChanges.values().asList();
  }

  public boolean furtherHiddenChanges() {
    return !nonVisibleChanges.isEmpty();
  }

  public int size() {
    return changeData.size() + nonVisibleChanges.size();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ids() + nonVisibleIds();
  }
}
