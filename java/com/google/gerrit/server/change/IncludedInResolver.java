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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.util.Collection;
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

/** Resolve in which tags and branches (or) refs a commit is included. */
public class IncludedInResolver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Resolve in which tags and branches a commit is included. */
  public static Result resolve(Repository repo, RevWalk rw, RevCommit commit) throws IOException {
    RevFlag flag = newFlag(rw);
    try {
      return new IncludedInResolver(repo, rw, flag).resolve(commit);
    } finally {
      rw.disposeFlag(flag);
    }
  }

  /** Resolve in which refs the commits are included in. */
  public static ImmutableMap<RevCommit, ImmutableList<Ref>> resolve(
      Repository repo, RevWalk rw, Set<RevCommit> commits, Set<Ref> refs) throws IOException {
    RevFlag flag = newFlag(rw);
    try {
      return new IncludedInResolver(repo, rw, flag).resolve(commits, refs);
    } finally {
      rw.disposeFlag(flag);
    }
  }

  private static RevFlag newFlag(RevWalk rw) {
    return rw.newFlag("CONTAINS_TARGET");
  }

  private final Repository repo;
  private final RevWalk rw;

  private final RevFlag containsTarget;
  private ListMultimap<RevCommit, String> commitToRef;
  private List<RevCommit> tipsByCommitTime;

  private IncludedInResolver(Repository repo, RevWalk rw, RevFlag containsTarget) {
    this.repo = repo;
    this.rw = rw;
    this.containsTarget = containsTarget;
  }

  private Result resolve(RevCommit target) throws IOException {
    RefDatabase refDb = repo.getRefDatabase();
    Collection<Ref> tags = refDb.getRefsByPrefix(Constants.R_TAGS);
    Collection<Ref> branches = refDb.getRefsByPrefix(Constants.R_HEADS);
    List<Ref> allTagsAndBranches = Lists.newArrayListWithCapacity(tags.size() + branches.size());
    allTagsAndBranches.addAll(tags);
    allTagsAndBranches.addAll(branches);
    parseCommits(allTagsAndBranches);
    Set<String> allMatchingTagsAndBranches = includedIn(target, tipsByCommitTime, 0);

    return new AutoValue_IncludedInResolver_Result(
        getMatchingRefNames(allMatchingTagsAndBranches, branches),
        getMatchingRefNames(allMatchingTagsAndBranches, tags));
  }

  private ImmutableMap<RevCommit, ImmutableList<Ref>> resolve(Set<RevCommit> targets, Set<Ref> refs)
      throws IOException {
    ImmutableMap.Builder<RevCommit, ImmutableList<Ref>> refsByCommit = ImmutableMap.builder();
    parseCommits(refs);
    for (RevCommit target : targets) {
      Set<String> matchingRefs = includedIn(target, tipsByCommitTime, 0);
      refsByCommit.put(target, getMatchingRefNames(matchingRefs, refs));
      rw.markStart(tipsByCommitTime);
      rw.reset();
    }
    return refsByCommit.build();
  }

  /** Resolves which tip refs include the target commit. */
  private Set<String> includedIn(RevCommit target, Collection<RevCommit> tips, int limit)
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
   * Returns the short names of refs which are as well in the matchingRefs list as well as in the
   * allRef list.
   */
  private static ImmutableList<Ref> getMatchingRefNames(
      Set<String> matchingRefs, Collection<Ref> allRefs) {
    return allRefs.stream()
        .filter(r -> matchingRefs.contains(r.getName()))
        .distinct()
        .collect(ImmutableList.toImmutableList());
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
        logger.atWarning().log(
            "Reference %s in %s points to dangling object %s",
            ref.getName(), repo.getDirectory(), ref.getObjectId());
        continue;
      }
      commitToRef.put(commit, ref.getName());
    }
    tipsByCommitTime =
        commitToRef.keySet().stream().sorted(comparing(RevCommit::getCommitTime)).collect(toList());
  }

  @AutoValue
  public abstract static class Result {
    public abstract ImmutableList<Ref> branches();

    public abstract ImmutableList<Ref> tags();
  }
}
