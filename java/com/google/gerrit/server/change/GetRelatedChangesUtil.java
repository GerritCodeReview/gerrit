// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Utility class that gets the ancestor changes and the descendent changes of a specific change. */
@Singleton
public class GetRelatedChangesUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;
  private final RelatedChangesSorter sorter;
  private final IndexConfig indexConfig;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  GetRelatedChangesUtil(
      Provider<InternalChangeQuery> queryProvider,
      RelatedChangesSorter sorter,
      IndexConfig indexConfig,
      ChangeData.Factory changeDataFactory) {
    this.queryProvider = queryProvider;
    this.sorter = sorter;
    this.indexConfig = indexConfig;
    this.changeDataFactory = changeDataFactory;
  }

  /**
   * Gets related changes of a specific change revision.
   *
   * @param changeData the change of the inputted revision.
   * @param basePs the revision that the method checks for related changes.
   * @return list of related changes, sorted via {@link RelatedChangesSorter}
   */
  public List<RelatedChangesSorter.PatchSetData> getRelated(ChangeData changeData, PatchSet basePs)
      throws IOException, PermissionBackendException {
    List<ChangeData> cds = getUnsortedRelated(changeData, basePs, false);
    if (cds.isEmpty()) {
      return Collections.emptyList();
    }
    return sorter.sort(cds, basePs);
  }

  /**
   * Gets ancestor changes of a specific change revision.
   *
   * @param changeData the change of the inputted revision.
   * @param basePs the revision that the method checks for related changes.
   * @param alwaysIncludeOriginalChange whether to return the given change when no ancestors found.
   * @return list of ancestor changes, sorted via {@link RelatedChangesSorter}
   */
  public List<RelatedChangesSorter.PatchSetData> getAncestors(
      ChangeData changeData, PatchSet basePs, boolean alwaysIncludeOriginalChange)
      throws IOException, PermissionBackendException {
    List<ChangeData> cds = getUnsortedRelated(changeData, basePs, alwaysIncludeOriginalChange);
    if (cds.isEmpty()) {
      return Collections.emptyList();
    }
    return sorter.sortAncestors(cds, basePs);
  }

  private List<ChangeData> getUnsortedRelated(
      ChangeData changeData, PatchSet basePs, boolean alwaysIncludeOriginalChange) {
    Set<String> groups = getAllGroups(changeData.patchSets());
    logger.atFine().log("groups = %s", groups);
    if (groups.isEmpty()) {
      return Collections.emptyList();
    }

    List<ChangeData> cds =
        InternalChangeQuery.byProjectGroups(
            queryProvider, indexConfig, changeData.project(), groups);
    if (cds.isEmpty()) {
      return Collections.emptyList();
    }
    if (cds.size() == 1 && cds.get(0).getId().equals(changeData.getId())) {
      return alwaysIncludeOriginalChange ? cds : Collections.emptyList();
    }

    return reloadChangeIfStale(cds, changeData, basePs);
  }

  private List<ChangeData> reloadChangeIfStale(
      List<ChangeData> changeDatasFromIndex, ChangeData wantedChange, PatchSet wantedPs) {
    checkArgument(
        wantedChange.getId().equals(wantedPs.id().changeId()),
        "change of wantedPs (%s) doesn't match wantedChange (%s)",
        wantedPs.id().changeId(),
        wantedChange.getId());

    List<ChangeData> changeDatas = new ArrayList<>(changeDatasFromIndex.size() + 1);
    changeDatas.addAll(changeDatasFromIndex);

    // Reload the change in case the patch set is absent.
    changeDatas.stream()
        .filter(
            cd -> cd.getId().equals(wantedPs.id().changeId()) && cd.patchSet(wantedPs.id()) == null)
        .forEach(ChangeData::reloadChange);

    if (changeDatas.stream().noneMatch(cd -> cd.getId().equals(wantedPs.id().changeId()))) {
      // The change of the wanted patch set is missing in the result from the index.
      // Load it from NoteDb and add it to the result.
      changeDatas.add(changeDataFactory.create(wantedChange.change()));
    }

    return changeDatas;
  }

  @VisibleForTesting
  public static Set<String> getAllGroups(Collection<PatchSet> patchSets) {
    return patchSets.stream().flatMap(ps -> ps.groups().stream()).collect(toSet());
  }
}
