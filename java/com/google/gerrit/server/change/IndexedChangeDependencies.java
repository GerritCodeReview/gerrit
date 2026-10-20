// Copyright (C) 2026 Monty Taylor
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.IndexedDependenciesInfo;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.CurrentRevisionParents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Joins stored direct-parent IDs against patch sets of the already visible query results. */
final class IndexedChangeDependencies {
  private record ProjectCommit(Project.NameKey project, String commitId) {}

  private IndexedChangeDependencies() {}

  /**
   * The caller must supply only changes in the permission-filtered response. This method does not
   * query for missing parents, load commits, or compute target-branch reachability. Missing stored
   * snapshots stay unavailable, including when other query options permit lazy loading.
   */
  static Map<Change.Id, IndexedDependenciesInfo> resolve(Collection<ChangeData> changes) {
    SetMultimap<ProjectCommit, PatchSet.Id> revisions = HashMultimap.create();
    for (ChangeData cd : changes) {
      if (cd.change().isMerged()) {
        continue;
      }
      for (PatchSet ps : cd.patchSets()) {
        revisions.put(new ProjectCommit(cd.project(), ps.commitId().name()), ps.id());
      }
    }

    Map<Change.Id, IndexedDependenciesInfo> result = new HashMap<>();
    for (ChangeData cd : changes) {
      if (cd.change().isMerged()) {
        continue;
      }
      CurrentRevisionParents stored = cd.indexedCurrentRevisionParents();
      PatchSet current = cd.currentPatchSet();
      if (stored == null
          || stored.version() != CurrentRevisionParents.VERSION
          || current == null
          || stored.patchSetNumber() != current.number()
          || !current.commitId().name().equals(stored.commitId())
          || stored.parentIds() == null) {
        continue;
      }
      IndexedDependenciesInfo info = new IndexedDependenciesInfo();
      info.revision = stored.commitId();
      info.patchSetNumber = stored.patchSetNumber();
      info.parents = new ArrayList<>();
      for (String parentId : stored.parentIds()) {
        Set<PatchSet.Id> matches = revisions.get(new ProjectCommit(cd.project(), parentId));
        if (matches.size() != 1) {
          continue;
        }
        PatchSet.Id match = matches.iterator().next();
        if (match.changeId().equals(cd.getId())) {
          continue;
        }
        IndexedDependenciesInfo.Parent parent = new IndexedDependenciesInfo.Parent();
        parent.changeNumber = match.changeId().get();
        parent.patchSetNumber = match.get();
        parent.commitId = parentId;
        info.parents.add(parent);
      }
      result.put(cd.getId(), info);
    }
    return result;
  }
}
