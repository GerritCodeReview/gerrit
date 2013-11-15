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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.IncludedInDetail;

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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Resolve in which tags and branches a commit is included.
 */
public class IncludedInResolver {

  private static final Logger log = LoggerFactory
      .getLogger(IncludedInResolver.class);

  public static IncludedInDetail resolve(final Repository repo,
      final RevWalk rw, final RevCommit commit) throws IOException {

    RefDatabase refDb = repo.getRefDatabase();
    Collection<Ref> tags = refDb.getRefs(Constants.R_TAGS).values();
    Collection<Ref> branches = refDb.getRefs(Constants.R_HEADS).values();
    List<Ref> allTagsAndBranches = Lists.newArrayListWithCapacity(
        tags.size() + branches.size());
    allTagsAndBranches.addAll(tags);
    allTagsAndBranches.addAll(branches);
    Set<String> allMatchingTagsAndBranches =
        includedIn(repo, rw, commit, allTagsAndBranches);

    IncludedInDetail detail = new IncludedInDetail();
    detail
        .setBranches(getMatchingRefNames(allMatchingTagsAndBranches, branches));
    detail.setTags(getMatchingRefNames(allMatchingTagsAndBranches, tags));

    return detail;
  }

  /**
   * Resolves which tip refs include the target commit.
   */
  private static Set<String> includedIn(final Repository repo, final RevWalk rw,
      final RevCommit target, final Collection<Ref> tipRefs) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {

    Set<String> result = Sets.newHashSet();

    Multimap<RevCommit, String> tipsAndCommits = parseCommits(repo, rw, tipRefs);

    List<RevCommit> tips = Lists.newArrayList(tipsAndCommits.keySet());
    Collections.sort(tips, new Comparator<RevCommit>() {
      @Override
      public int compare(RevCommit c1, RevCommit c2) {
        return c1.getCommitTime() - c2.getCommitTime();
      }
    });

    Set<RevCommit> targetReachableFrom = Sets.newHashSet();
    targetReachableFrom.add(target);

    for (RevCommit tip : tips) {
      boolean commitFound = false;
      rw.resetRetain(RevFlag.UNINTERESTING);
      rw.markStart(tip);
      for (RevCommit commit : rw) {
        if (targetReachableFrom.contains(commit)) {
          commitFound = true;
          targetReachableFrom.add(tip);
          result.addAll(tipsAndCommits.get(tip));
          break;
        }
      }
      if (!commitFound) {
        rw.markUninteresting(tip);
      }
    }

    return result;
  }

  /**
   * Returns the short names of refs which are as well in the matchingRefs list
   * as well as in the allRef list.
   */
  private static List<String> getMatchingRefNames(Set<String> matchingRefs,
      Collection<Ref> allRefs) {
    List<String> refNames = Lists.newArrayListWithCapacity(matchingRefs.size());
    for (Ref r : allRefs) {
      if (matchingRefs.contains(r.getName())) {
        refNames.add(Repository.shortenRefName(r.getName()));
      }
    }
    return refNames;
  }

  /**
   * Parse commit of ref and store the relation between ref and commit.
   */
  private static Multimap<RevCommit, String> parseCommits(final Repository repo,
      final RevWalk rw, final Collection<Ref> refs) throws IOException {
    Multimap<RevCommit, String> result = LinkedListMultimap.create();
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
        log.warn("Reference " + ref.getName() + " in " + repo.getDirectory()
            + " points to dangling object " + ref.getObjectId());
        continue;
      }
      result.put(commit, ref.getName());
    }
    return result;
  }
}
