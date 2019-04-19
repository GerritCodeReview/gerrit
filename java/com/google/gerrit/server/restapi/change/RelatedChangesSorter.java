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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
class RelatedChangesSorter {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;

  @Inject
  RelatedChangesSorter(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      ProjectCache projectCache) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  public List<PatchSetData> sort(List<ChangeData> in, PatchSet startPs)
      throws IOException, PermissionBackendException {
    checkArgument(!in.isEmpty(), "Input may not be empty");
    // Map of all patch sets, keyed by commit SHA-1.
    Map<String, PatchSetData> byId = collectById(in);
    PatchSetData start = byId.get(startPs.getRevision().get());
    checkArgument(start != null, "%s not found in %s", startPs, in);

    // Map of patch set -> immediate parent.
    ListMultimap<PatchSetData, PatchSetData> parents =
        MultimapBuilder.hashKeys(in.size()).arrayListValues(3).build();
    // Map of patch set -> immediate children.
    ListMultimap<PatchSetData, PatchSetData> children =
        MultimapBuilder.hashKeys(in.size()).arrayListValues(3).build();
    // All other patch sets of the same change as startPs.
    List<PatchSetData> otherPatchSetsOfStart = new ArrayList<>();

    for (ChangeData cd : in) {
      for (PatchSet ps : cd.patchSets()) {
        PatchSetData thisPsd = requireNonNull(byId.get(ps.getRevision().get()));
        if (cd.getId().equals(start.id()) && !ps.getId().equals(start.psId())) {
          otherPatchSetsOfStart.add(thisPsd);
        }
        for (RevCommit p : thisPsd.commit().getParents()) {
          PatchSetData parentPsd = byId.get(p.name());
          if (parentPsd != null) {
            parents.put(thisPsd, parentPsd);
            children.put(parentPsd, thisPsd);
          }
        }
      }
    }

    Collection<PatchSetData> ancestors = walkAncestors(parents, start);
    List<PatchSetData> descendants =
        walkDescendants(children, start, otherPatchSetsOfStart, ancestors);
    List<PatchSetData> result = new ArrayList<>(ancestors.size() + descendants.size() - 1);
    result.addAll(Lists.reverse(descendants));
    result.addAll(ancestors);
    return result;
  }

  private Map<String, PatchSetData> collectById(List<ChangeData> in) throws IOException {
    Project.NameKey project = in.get(0).change().getProject();
    Map<String, PatchSetData> result = Maps.newHashMapWithExpectedSize(in.size() * 3);
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.setRetainBody(true);
      for (ChangeData cd : in) {
        checkArgument(
            cd.change().getProject().equals(project),
            "Expected change %s in project %s, found %s",
            cd.getId(),
            project,
            cd.change().getProject());
        for (PatchSet ps : cd.patchSets()) {
          String id = ps.getRevision().get();
          RevCommit c = rw.parseCommit(ObjectId.fromString(id));
          PatchSetData psd = PatchSetData.create(cd, ps, c);
          result.put(id, psd);
        }
      }
    }
    return result;
  }

  private Collection<PatchSetData> walkAncestors(
      ListMultimap<PatchSetData, PatchSetData> parents, PatchSetData start)
      throws PermissionBackendException, IOException {
    LinkedHashSet<PatchSetData> result = new LinkedHashSet<>();
    Deque<PatchSetData> pending = new ArrayDeque<>();
    pending.add(start);
    while (!pending.isEmpty()) {
      PatchSetData psd = pending.remove();
      if (result.contains(psd) || !isVisible(psd)) {
        continue;
      }
      result.add(psd);
      pending.addAll(Lists.reverse(parents.get(psd)));
    }
    return result;
  }

  private List<PatchSetData> walkDescendants(
      ListMultimap<PatchSetData, PatchSetData> children,
      PatchSetData start,
      List<PatchSetData> otherPatchSetsOfStart,
      Iterable<PatchSetData> ancestors)
      throws PermissionBackendException, IOException {
    Set<Change.Id> alreadyEmittedChanges = new HashSet<>();
    addAllChangeIds(alreadyEmittedChanges, ancestors);

    // Prefer descendants found by following the original patch set passed in.
    List<PatchSetData> result =
        walkDescendentsImpl(alreadyEmittedChanges, children, ImmutableList.of(start));
    addAllChangeIds(alreadyEmittedChanges, result);

    // Then, go back and add new indirect descendants found by following any
    // other patch sets of start. These show up after all direct descendants,
    // because we wouldn't know where in the walk to insert them.
    result.addAll(walkDescendentsImpl(alreadyEmittedChanges, children, otherPatchSetsOfStart));
    return result;
  }

  private static void addAllChangeIds(
      Collection<Change.Id> changeIds, Iterable<PatchSetData> psds) {
    for (PatchSetData psd : psds) {
      changeIds.add(psd.id());
    }
  }

  private List<PatchSetData> walkDescendentsImpl(
      Set<Change.Id> alreadyEmittedChanges,
      ListMultimap<PatchSetData, PatchSetData> children,
      List<PatchSetData> start)
      throws PermissionBackendException, IOException {
    if (start.isEmpty()) {
      return ImmutableList.of();
    }
    Map<Change.Id, PatchSet.Id> maxPatchSetIds = new HashMap<>();
    Set<PatchSetData> seen = new HashSet<>();
    List<PatchSetData> allPatchSets = new ArrayList<>();
    Deque<PatchSetData> pending = new ArrayDeque<>();
    pending.addAll(start);
    while (!pending.isEmpty()) {
      PatchSetData psd = pending.remove();
      if (seen.contains(psd) || !isVisible(psd)) {
        continue;
      }
      seen.add(psd);
      if (!alreadyEmittedChanges.contains(psd.id())) {
        // Don't emit anything for changes that were previously emitted, even
        // though different patch sets might show up later. However, do
        // continue walking through them for the purposes of finding indirect
        // descendants.
        PatchSet.Id oldMax = maxPatchSetIds.get(psd.id());
        if (oldMax == null || psd.psId().get() > oldMax.get()) {
          maxPatchSetIds.put(psd.id(), psd.psId());
        }
        allPatchSets.add(psd);
      }
      // Depth-first search with newest children first.
      for (PatchSetData child : children.get(psd)) {
        pending.addFirst(child);
      }
    }

    // If we saw the same change multiple times, prefer the latest patch set.
    List<PatchSetData> result = new ArrayList<>(allPatchSets.size());
    for (PatchSetData psd : allPatchSets) {
      if (requireNonNull(maxPatchSetIds.get(psd.id())).equals(psd.psId())) {
        result.add(psd);
      }
    }
    return result;
  }

  private boolean isVisible(PatchSetData psd) throws PermissionBackendException, IOException {
    PermissionBackend.WithUser perm = permissionBackend.currentUser();
    try {
      perm.change(psd.data()).check(ChangePermission.READ);
    } catch (AuthException e) {
      return false;
    }
    ProjectState state = projectCache.checkedGet(psd.data().project());
    return state != null && state.statePermitsRead();
  }

  @AutoValue
  abstract static class PatchSetData {
    @VisibleForTesting
    static PatchSetData create(ChangeData cd, PatchSet ps, RevCommit commit) {
      return new AutoValue_RelatedChangesSorter_PatchSetData(cd, ps, commit);
    }

    abstract ChangeData data();

    abstract PatchSet patchSet();

    abstract RevCommit commit();

    PatchSet.Id psId() {
      return patchSet().getId();
    }

    Change.Id id() {
      return psId().changeId();
    }

    @Override
    public int hashCode() {
      return Objects.hash(patchSet().getId(), commit());
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof PatchSetData)) {
        return false;
      }
      PatchSetData o = (PatchSetData) obj;
      return Objects.equals(patchSet().getId(), o.patchSet().getId())
          && Objects.equals(commit(), o.commit());
    }
  }
}
