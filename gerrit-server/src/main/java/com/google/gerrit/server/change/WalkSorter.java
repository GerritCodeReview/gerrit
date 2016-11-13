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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
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
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper to sort {@link ChangeData}s based on {@link RevWalk} ordering.
 *
 * <p>Split changes by project, and map each change to a single commit based on the latest patch
 * set. The set of patch sets considered may be limited by calling {@link
 * #includePatchSets(Iterable)}. Perform a standard {@link RevWalk} on each project repository, do
 * an approximate topo sort, and record the order in which each change's commit is seen.
 *
 * <p>Once an order within each project is determined, groups of changes are sorted based on the
 * project name. This is slightly more stable than sorting on something like the commit or change
 * timestamp, as it will not unexpectedly reorder large groups of changes on subsequent calls if one
 * of the changes was updated.
 */
class WalkSorter {
  private static final Logger log = LoggerFactory.getLogger(WalkSorter.class);

  private static final Ordering<List<PatchSetData>> PROJECT_LIST_SORTER =
      Ordering.natural()
          .nullsFirst()
          .onResultOf(
              (List<PatchSetData> in) -> {
                if (in == null || in.isEmpty()) {
                  return null;
                }
                try {
                  return in.get(0).data().change().getProject();
                } catch (OrmException e) {
                  throw new IllegalStateException(e);
                }
              });

  private final GitRepositoryManager repoManager;
  private final Set<PatchSet.Id> includePatchSets;
  private boolean retainBody;

  @Inject
  WalkSorter(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
    includePatchSets = new HashSet<>();
  }

  public WalkSorter includePatchSets(Iterable<PatchSet.Id> patchSets) {
    Iterables.addAll(includePatchSets, patchSets);
    return this;
  }

  public WalkSorter setRetainBody(boolean retainBody) {
    this.retainBody = retainBody;
    return this;
  }

  public Iterable<PatchSetData> sort(Iterable<ChangeData> in) throws OrmException, IOException {
    Multimap<Project.NameKey, ChangeData> byProject = ArrayListMultimap.create();
    for (ChangeData cd : in) {
      byProject.put(cd.change().getProject(), cd);
    }

    List<List<PatchSetData>> sortedByProject = new ArrayList<>(byProject.keySet().size());
    for (Map.Entry<Project.NameKey, Collection<ChangeData>> e : byProject.asMap().entrySet()) {
      sortedByProject.add(sortProject(e.getKey(), e.getValue()));
    }
    Collections.sort(sortedByProject, PROJECT_LIST_SORTER);
    return Iterables.concat(sortedByProject);
  }

  private List<PatchSetData> sortProject(Project.NameKey project, Collection<ChangeData> in)
      throws OrmException, IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.setRetainBody(retainBody);
      Multimap<RevCommit, PatchSetData> byCommit = byCommit(rw, in);
      if (byCommit.isEmpty()) {
        return ImmutableList.of();
      } else if (byCommit.size() == 1) {
        return ImmutableList.of(byCommit.values().iterator().next());
      }

      // Walk from all patch set SHA-1s, and terminate as soon as we've found
      // everything we're looking for. This is equivalent to just sorting the
      // list of commits by the RevWalk's configured order.
      //
      // Partially topo sort the list, ensuring no parent is emitted before a
      // direct child that is also in the input set. This preserves the stable,
      // expected sort in the case where many commits share the same timestamp,
      // e.g. a quick rebase. It also avoids JGit's topo sort, which slurps all
      // interesting commits at the beginning, which is a problem since we don't
      // know which commits to mark as uninteresting. Finding a reasonable set
      // of commits to mark uninteresting (the "rootmost" set) is at least as
      // difficult as just implementing this partial topo sort ourselves.
      //
      // (This is slightly less efficient than JGit's topo sort, which uses a
      // private in-degree field in RevCommit rather than multimaps. We assume
      // the input size is small enough that this is not an issue.)

      Set<RevCommit> commits = byCommit.keySet();
      Multimap<RevCommit, RevCommit> children = collectChildren(commits);
      Multimap<RevCommit, RevCommit> pending = ArrayListMultimap.create();
      Deque<RevCommit> todo = new ArrayDeque<>();

      RevFlag done = rw.newFlag("done");
      markStart(rw, commits);
      int expected = commits.size();
      int found = 0;
      RevCommit c;
      List<PatchSetData> result = new ArrayList<>(expected);
      while (found < expected && (c = rw.next()) != null) {
        if (!commits.contains(c)) {
          continue;
        }
        todo.clear();
        todo.add(c);
        int i = 0;
        while (!todo.isEmpty()) {
          // Sanity check: we can't pop more than N pending commits, otherwise
          // we have an infinite loop due to programmer error or something.
          checkState(++i <= commits.size(), "Too many pending steps while sorting %s", commits);
          RevCommit t = todo.removeFirst();
          if (t.has(done)) {
            continue;
          }
          boolean ready = true;
          for (RevCommit child : children.get(t)) {
            if (!child.has(done)) {
              pending.put(child, t);
              ready = false;
            }
          }
          if (ready) {
            found += emit(t, byCommit, result, done);
            todo.addAll(pending.get(t));
          }
        }
      }
      return result;
    }
  }

  private static Multimap<RevCommit, RevCommit> collectChildren(Set<RevCommit> commits) {
    Multimap<RevCommit, RevCommit> children = ArrayListMultimap.create();
    for (RevCommit c : commits) {
      for (RevCommit p : c.getParents()) {
        if (commits.contains(p)) {
          children.put(p, c);
        }
      }
    }
    return children;
  }

  private static int emit(
      RevCommit c,
      Multimap<RevCommit, PatchSetData> byCommit,
      List<PatchSetData> result,
      RevFlag done) {
    if (c.has(done)) {
      return 0;
    }
    c.add(done);
    Collection<PatchSetData> psds = byCommit.get(c);
    if (!psds.isEmpty()) {
      result.addAll(psds);
      return 1;
    }
    return 0;
  }

  private Multimap<RevCommit, PatchSetData> byCommit(RevWalk rw, Collection<ChangeData> in)
      throws OrmException, IOException {
    Multimap<RevCommit, PatchSetData> byCommit = ArrayListMultimap.create(in.size(), 1);
    for (ChangeData cd : in) {
      PatchSet maxPs = null;
      for (PatchSet ps : cd.patchSets()) {
        if (shouldInclude(ps) && (maxPs == null || ps.getId().get() > maxPs.getId().get())) {
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
        log.warn("missing commit " + id.name() + " for patch set " + maxPs.getId(), e);
      }
    }
    return byCommit;
  }

  private boolean shouldInclude(PatchSet ps) {
    return includePatchSets.isEmpty() || includePatchSets.contains(ps.getId());
  }

  private static void markStart(RevWalk rw, Iterable<RevCommit> commits) throws IOException {
    for (RevCommit c : commits) {
      rw.markStart(c);
    }
  }

  @AutoValue
  abstract static class PatchSetData {
    @VisibleForTesting
    static PatchSetData create(ChangeData cd, PatchSet ps, RevCommit commit) {
      return new AutoValue_WalkSorter_PatchSetData(cd, ps, commit);
    }

    abstract ChangeData data();

    abstract PatchSet patchSet();

    abstract RevCommit commit();
  }
}
