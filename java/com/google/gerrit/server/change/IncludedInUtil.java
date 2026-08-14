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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

public class IncludedInUtil {

  /**
   * Clock skew allowance, in seconds, applied when comparing a ref's tip commit time against the
   * commit being looked for. Matches the allowance used by JGit's {@code
   * RevWalkUtils#findBranchesReachableFrom}.
   */
  private static final int SKEW_SECS = 24 * 3600;

  /**
   * Sorts the collection of {@code Ref} instances by its tip commit time.
   *
   * @param refs collection to be sorted
   * @param revWalk {@code RevWalk} instance for parsing ref's tip commit
   * @return sorted list of refs
   */
  public static List<Ref> getSortedRefs(Collection<Ref> refs, RevWalk revWalk) {
    return refs.stream()
        .sorted(
            comparing(
                ref -> {
                  try {
                    return revWalk.parseCommit(ref.getObjectId()).getCommitTime();
                  } catch (IOException e) {
                    // Ignore and continue to sort
                  }
                  return 0;
                }))
        .collect(toList());
  }

  /**
   * Drops refs that cannot contain {@code commit} because their tip commit is older than it.
   *
   * <p>If a ref contains {@code commit}, the ref's tip must be at least as new as {@code commit},
   * so any ref whose tip is older (beyond a clock skew allowance) can be skipped without walking
   * its history at all. On repositories with many refs this avoids a large amount of pointless
   * traversal.
   *
   * <p>Annotated tags are peeled before the comparison, so a ref pointing at a tag object is
   * compared using the commit the tag points to. Refs that do not resolve to a commit, or that
   * cannot be parsed, are retained so that this remains a pure optimization and never changes which
   * refs are reported as containing {@code commit}.
   *
   * @param commit the commit being looked for
   * @param refs candidate refs
   * @param revWalk {@code RevWalk} instance used for parsing and peeling
   * @return refs that may contain {@code commit}
   */
  public static List<Ref> filterRefsNewerThan(
      RevCommit commit, Collection<Ref> refs, RevWalk revWalk) {
    List<Ref> filtered = new ArrayList<>(refs.size());
    for (Ref ref : refs) {
      RevCommit tip;
      try {
        RevObject obj = revWalk.peel(revWalk.parseAny(ref.getObjectId()));
        if (!(obj instanceof RevCommit)) {
          // Not a commit (e.g. a tag of a blob or tree); leave it for getMergedInto to handle.
          filtered.add(ref);
          continue;
        }
        tip = (RevCommit) obj;
      } catch (IOException e) {
        // Keep the ref rather than risk dropping one that does contain the commit.
        filtered.add(ref);
        continue;
      }
      if (tip.getCommitTime() + SKEW_SECS < commit.getCommitTime()) {
        continue;
      }
      filtered.add(ref);
    }
    return filtered;
  }
}
