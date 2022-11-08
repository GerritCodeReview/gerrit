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

package com.google.gerrit.server.submit;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

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
    changeData = index(changes, ImmutableList.of());
    nonVisibleChanges = index(hiddenChanges, changeData.keySet());
  }

  public ChangeSet(ChangeData change, boolean visible) {
    this(visible ? ImmutableList.of(change) : ImmutableList.of(), ImmutableList.of(change));
  }

  public ImmutableSet<Change.Id> ids() {
    return changeData.keySet();
  }

  public ImmutableMap<Change.Id, ChangeData> changesById() {
    return changeData;
  }

  public ListMultimap<BranchNameKey, ChangeData> changesByBranch() {
    ListMultimap<BranchNameKey, ChangeData> ret =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (ChangeData cd : changeData.values()) {
      ret.put(cd.change().getDest(), cd);
    }
    return ret;
  }

  /**
   * If the ChangeSet is a representation of a change chain, returns the changes sorted by ancestry
   *
   * @param repo DO NOT SUBMIT
   * @param queryProvider DO NOT SUBMIT
   * @param rw DO NOT SUBMIT
   * @return The sorted change chain. The chain most ancient ancestor is returned first
   * @throws IOException if accessing the repository fails.
   * @throws UnprocessableEntityException if sorting the chain is not possible
   */
  public ImmutableList<ChangeData> sortedChangeChainByAncestry(
      Repository repo, Provider<InternalChangeQuery> queryProvider, RevWalk rw)
      throws IOException, UnprocessableEntityException {
    ListMultimap<BranchNameKey, ChangeData> changesByBranch = changesByBranch();
    if (changesByBranch.keySet().size() != 1) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. ChangeSet contains changes from different branches.");
    }
    BranchNameKey destBranch = changesByBranch.keySet().iterator().next();

    Map<Change.Id, Change.Id> childToParent = new HashMap<>();
    for (Map.Entry<Change.Id, ChangeData> e : changeData.entrySet()) {
      childToParent.put(
          e.getKey(), getParentChangeId(repo, queryProvider, rw, destBranch, e.getValue()));
    }

    Set<Change.Id> changesWithNoChildren =
        changeData.keySet().stream()
            .filter(e -> !childToParent.values().contains(e))
            .collect(Collectors.toSet());
    if (changesWithNoChildren.size() != 1) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. There should be exactly one change in the ChangeSet with no presented children.");
    }

    List<ChangeData> res = new ArrayList<>();
    Change.Id curr = changesWithNoChildren.iterator().next();
    while (changeData.containsKey(curr)) {
      res.add(changeData.get(curr));
      curr = childToParent.get(curr);
    }
    if (res.size() != changeData.size()) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. No single ancestry chain that covers the whole ChangeSet.");
    }
    return Lists.reverse(res).stream().collect(toImmutableList());
  }

  private Change.Id getParentChangeId(
      Repository repo,
      Provider<InternalChangeQuery> queryProvider,
      RevWalk rw,
      BranchNameKey destBranch,
      ChangeData child)
      throws IOException, UnprocessableEntityException {
    RevCommit childCommit = rw.parseCommit(child.currentPatchSet().commitId());

    if (childCommit.getParentCount() > 1) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. Multiple parents for commit " + childCommit.name());
    } else if (childCommit.getParentCount() == 0) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. No parents for commit " + childCommit.name());
    }

    ObjectId parentId = childCommit.getParent(0);
    List<ChangeData> parentChanges =
        queryProvider.get().byBranchCommit(destBranch, parentId.name());
    if (parentChanges.isEmpty()) {
      // The change is dependent on a merged PatchSet or have no PatchSet dependencies at all.
      Ref destRef = repo.getRefDatabase().exactRef(destBranch.branch());
      parentId = destRef.getObjectId();
      parentChanges = queryProvider.get().byBranchCommit(destBranch, parentId.getName());
    }
    if (parentChanges.isEmpty()) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. No change found with commit "
              + parentId.name()
              + " in branch "
              + destBranch.shortName());
    }
    if (parentChanges.size() > 1) {
      throw new UnprocessableEntityException(
          "Cannot sort changes by ancestry. Multiple changes founded with commit "
              + parentId.name()
              + " in branch "
              + destBranch.shortName());
    }
    return parentChanges.get(0).getId();
  }

  public ImmutableCollection<ChangeData> changes() {
    return changeData.values();
  }

  public ImmutableSet<Project.NameKey> projects() {
    ImmutableSet.Builder<Project.NameKey> ret = ImmutableSet.builder();
    for (ChangeData cd : changeData.values()) {
      ret.add(cd.project());
    }
    return ret.build();
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
