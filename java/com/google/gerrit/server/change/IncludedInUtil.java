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
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;

public class IncludedInUtil {

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
}
