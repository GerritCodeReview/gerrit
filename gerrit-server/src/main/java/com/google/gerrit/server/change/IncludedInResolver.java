// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolve in which tags and branches a commit is included. */
public class IncludedInResolver {

  private static final Logger log = LoggerFactory.getLogger(IncludedInResolver.class);

  public static Result resolve(Repository repo, RevWalk rw, RevCommit commit) throws IOException {
    RevFlag flag = newFlag(rw);
    try {
      return new IncludedInResolver(repo, rw, commit, flag).resolve();
    } finally {
      rw.disposeFlag(flag);
    }
  }

  public static boolean includedInOne(
      final Repository repo, RevWalk rw, RevCommit commit, Collection<Ref> refs)
      throws IOException {
    RevFlag flag = newFlag(rw);
    try {
      return new IncludedInResolver(repo, rw, commit, flag).includedInOne(refs);
    } finally {
      rw.disposeFlag(flag);
    }
  }

  private static RevFlag newFlag(RevWalk rw) {
    return rw.newFlag("CONTAINS_TARGET");
  }

  private final Repository repo;
  private final RevWalk rw;
  private final RevCommit target;

  private final RevFlag containsTarget;
  private ListMultimap<RevCommit, String> commitToRef;
  private List<RevCommit> tipsByCommitTime;

  private IncludedInResolver(
      Repository repo, RevWalk rw, RevCommit target, RevFlag containsTarget) {
    this.repo = repo;
    this.rw = rw;
    this.target = target;
    this.containsTarget = containsTarget;
  }

  private Result resolve() throws IOException {
    RefDatabase refDb = repo.getRefDatabase();
    Collection<Ref> tags = refDb.getRefs(Constants.R_TAGS).values();
    Collection<Ref> branches = refDb.getRefs(Constants.R_HEADS).values();
    List<Ref> allTagsAndBranches = Lists.newArrayListWithCapacity(tags.size() + branches.size());
    allTagsAndBranches.addAll(tags);
    allTagsAndBranches.addAll(branches);
    parseCommits(allTagsAndBranches);
    Set<String> allMatchingTagsAndBranches = includedIn(tipsByCommitTime, 0);

    Result detail = new Result();
    detail.setBranches(getMatchingRefNames(allMatchingTagsAndBranches, branches));
    detail.setTags(getMatchingRefNames(allMatchingTagsAndBranches, tags));

    return detail;
  }

  private boolean includedInOne(Collection<Ref> refs) throws IOException {
    parseCommits(refs);
    List<RevCommit> before = new ArrayList<>();
    List<RevCommit> after = new ArrayList<>();
    partition(before, after);
    rw.reset();
    // It is highly likely that the target is reachable from the "after" set
    // Within the "before" set we are trying to handle cases arising from clock skew
    return !includedIn(after, 1).isEmpty() || !includedIn(before, 1).isEmpty();
  }

  /** Resolves which tip refs include the target commit. */
  private Set<String> includedIn(Collection<RevCommit> tips, int limit)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    Set<String> result = new HashSet<>();
    for (RevCommit tip : tips) {
      boolean commitFound = false;
      rw.resetRetain(RevFlag.UNINTERESTING, containsTarget);
      rw.markStart(tip);
      for (RevCommit commit : rw) {
        if (commit.equals(target) || commit.has(containsTarget)) {
          commitFound = true;
          tip.add(containsTarget);
          result.addAll(commitToRef.get(tip));
          break;
        }
      }
      if (!commitFound) {
        rw.markUninteresting(tip);
      } else if (0 < limit && limit < result.size()) {
        break;
      }
    }
    return result;
  }

  /**
   * Partition the reference tips into two sets:
   *
   * <ul>
   *   <li>before = commits with time < target.getCommitTime()
   *   <li>after = commits with time >= target.getCommitTime()
   * </ul>
   *
   * Each of the before/after lists is sorted by the the commit time.
   *
   * @param before
   * @param after
   */
  private void partition(List<RevCommit> before, List<RevCommit> after) {
    int insertionPoint =
        Collections.binarySearch(
            tipsByCommitTime,
            target,
            new Comparator<RevCommit>() {
              @Override
              public int compare(RevCommit c1, RevCommit c2) {
                return c1.getCommitTime() - c2.getCommitTime();
              }
            });
    if (insertionPoint < 0) {
      insertionPoint = -(insertionPoint + 1);
    }
    if (0 < insertionPoint) {
      before.addAll(tipsByCommitTime.subList(0, insertionPoint));
    }
    if (insertionPoint < tipsByCommitTime.size()) {
      after.addAll(tipsByCommitTime.subList(insertionPoint, tipsByCommitTime.size()));
    }
  }

  /**
   * Returns the short names of refs which are as well in the matchingRefs list as well as in the
   * allRef list.
   */
  private static List<String> getMatchingRefNames(
      Set<String> matchingRefs, Collection<Ref> allRefs) {
    List<String> refNames = Lists.newArrayListWithCapacity(matchingRefs.size());
    for (Ref r : allRefs) {
      if (matchingRefs.contains(r.getName())) {
        refNames.add(Repository.shortenRefName(r.getName()));
      }
    }
    return refNames;
  }

  /** Parse commit of ref and store the relation between ref and commit. */
  private void parseCommits(Collection<Ref> refs) throws IOException {
    if (commitToRef != null) {
      return;
    }
    commitToRef = LinkedListMultimap.create();
    for (Ref ref : refs) {
      final RevCommit commit;
      try {
        commit = rw.parseCommit(ref.getObjectId());
      } catch (IncorrectObjectTypeException notCommit) {
        // Its OK for a tag reference to point to a blob or a tree, this
        // is common in the Linux kernel or git.git repository.
        //
        continue;
      } catch (MissingObjectException notHere) {
        // Log the problem with this branch, but keep processing.
        //
        log.warn(
            "Reference "
                + ref.getName()
                + " in "
                + repo.getDirectory()
                + " points to dangling object "
                + ref.getObjectId());
        continue;
      }
      commitToRef.put(commit, ref.getName());
    }
    tipsByCommitTime = Lists.newArrayList(commitToRef.keySet());
    sortOlderFirst(tipsByCommitTime);
  }

  private void sortOlderFirst(List<RevCommit> tips) {
    Collections.sort(
        tips,
        new Comparator<RevCommit>() {
          @Override
          public int compare(RevCommit c1, RevCommit c2) {
            return c1.getCommitTime() - c2.getCommitTime();
          }
        });
  }

  public static class Result {
    private List<String> branches;
    private List<String> tags;

    public Result() {}

    public void setBranches(List<String> b) {
      Collections.sort(b);
      branches = b;
    }

    public List<String> getBranches() {
      return branches;
    }

    public void setTags(List<String> t) {
      Collections.sort(t);
      tags = t;
    }

    public List<String> getTags() {
      return tags;
    }
  }
}
