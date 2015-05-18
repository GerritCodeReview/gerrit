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

package com.google.gerrit.server.change;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper to sort {@link ChangeData}s based on {@link RevWalk} ordering.
 * <p>
 * Split changes by project, and map each change to a single commit based on the
 * latest patch set. The set of patch sets considered may be limited by calling
 * {@link #includePatchSets(Set)}. Perform a standard {@link RevWalk} on each
 * project repository, and record the order in which each change's commit is
 * seen.
 * <p>
 * Once an order within each project is determined, groups of changes are sorted
 * based on the project name. This is slightly more stable than sorting on
 * something like the commit or change timestamp, as it will not unexpectedly
 * reorder large groups of changes on subsequent calls if one of the changes was
 * updated.
 */
class WalkSorter {
  private static final Logger log =
      LoggerFactory.getLogger(WalkSorter.class);

  private static final Ordering<List<PatchSetData>> PROJECT_LIST_SORTER =
      Ordering.natural().nullsFirst()
          .onResultOf(
            new Function<List<PatchSetData>, Project.NameKey>() {
              @Override
              public Project.NameKey apply(List<PatchSetData> in) {
                if (in == null || in.isEmpty()) {
                  return null;
                }
                try {
                  return in.get(0).data().change().getProject();
                } catch (OrmException e) {
                  throw new IllegalStateException(e);
                }
              }
            });

  private final GitRepositoryManager repoManager;
  private final Set<PatchSet.Id> includePatchSets;
  private boolean parseBody;

  @Inject
  WalkSorter(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
    includePatchSets = new HashSet<>();
  }

  public WalkSorter includePatchSets(Iterable<PatchSet.Id> patchSets) {
    Iterables.addAll(includePatchSets, patchSets);
    return this;
  }

  public WalkSorter parseBody(boolean parseBody) {
    this.parseBody = parseBody;
    return this;
  }

  public Iterable<PatchSetData> sort(Iterable<ChangeData> in)
      throws OrmException, IOException {
    Multimap<Project.NameKey, ChangeData> byProject =
        ArrayListMultimap.create();
    for (ChangeData cd : in) {
      byProject.put(cd.change().getProject(), cd);
    }

    List<List<PatchSetData>> sortedByProject =
        new ArrayList<>(byProject.keySet().size());
    for (Map.Entry<Project.NameKey, Collection<ChangeData>> e
        : byProject.asMap().entrySet()) {
      sortedByProject.add(sortProject(e.getKey(), e.getValue()));
    }
    Collections.sort(sortedByProject, PROJECT_LIST_SORTER);
    return Iterables.concat(sortedByProject);
  }

  private List<PatchSetData> sortProject(Project.NameKey project,
      Collection<ChangeData> in) throws OrmException, IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.sort(RevSort.TOPO);
      Multimap<RevCommit, PatchSetData> byCommit = byCommit(rw, in);
      if (byCommit.isEmpty()) {
        return ImmutableList.of();
      }

      // Walk from all patch set SHA-1s, and terminate as soon as we've found
      // everything we're looking for. This is equivalent to just sorting the
      // list of commits by the RevWalk's configured order.
      for (RevCommit c : byCommit.keySet()) {
        rw.markStart(c);
      }

      int expected = byCommit.keySet().size();
      int found = 0;
      RevCommit c;
      List<PatchSetData> result = new ArrayList<>(expected);
      while (found < expected && (c = rw.next()) != null) {
        Collection<PatchSetData> psds = byCommit.get(c);
        if (!psds.isEmpty()) {
          found++;
          for (PatchSetData psd : psds) {
            if (parseBody) {
              rw.parseBody(psd.commit());
            }
            result.add(psd);
          }
        }
      }
      return result;
    }
  }

  private Multimap<RevCommit, PatchSetData> byCommit(RevWalk rw,
      Collection<ChangeData> in) throws OrmException, IOException {
    Multimap<RevCommit, PatchSetData> byCommit =
        ArrayListMultimap.create(in.size(), 1);
    for (ChangeData cd : in) {
      PatchSet maxPs = null;
      for (PatchSet ps : cd.patchSets()) {
        if (shouldInclude(ps)
            && (maxPs == null || ps.getId().get() > maxPs.getId().get())) {
          maxPs = ps;
        }
      }
      if (maxPs == null) {
       continue; // No patch sets matched.
      }
      ObjectId id = ObjectId.fromString(maxPs.getRevision().get());
      try {
        RevCommit c = rw.parseCommit(id);
        byCommit.put(c, PatchSetData.create(cd, maxPs, c));
      } catch (MissingObjectException | IncorrectObjectTypeException e) {
        log.warn(
            "missing commit " + id.name() + " for patch set " + maxPs.getId(),
            e);
      }
    }
    return byCommit;
  }

  private boolean shouldInclude(PatchSet ps) {
    return includePatchSets.isEmpty() || includePatchSets.contains(ps.getId());
  }

  @AutoValue
  static abstract class PatchSetData {
    @VisibleForTesting
    static PatchSetData create(ChangeData cd, PatchSet ps, RevCommit commit) {
      return new AutoValue_WalkSorter_PatchSetData(cd, ps, commit);
    }

    abstract ChangeData data();
    abstract PatchSet patchSet();
    abstract RevCommit commit();
  }
}
