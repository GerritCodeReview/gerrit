// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.OrPredicate;
import com.google.gerrit.index.query.PostFilterablePredicate;
import com.google.gerrit.index.query.Predicate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Predicate to match any number of BranchNameKeys with O(1) efficiency.
 *
 * <p>The index is queried for the project and ref of each branch, while {@link #match(ChangeData)}
 * is a single set lookup on the change's destination branch. This makes it a {@link
 * PostFilterablePredicate}: it can be evaluated in memory when a query has too many index terms.
 */
public class BranchSetIndexPredicate extends OrPredicate<ChangeData>
    implements PostFilterablePredicate<ChangeData> {
  private final String name;
  private final Set<BranchNameKey> branches;

  public BranchSetIndexPredicate(String name, Set<BranchNameKey> branches) throws StorageException {
    super(getPredicates(branches));
    this.name = name;
    this.branches = branches;
  }

  @Override
  public boolean match(ChangeData changeData) {
    Change change = changeData.change();
    if (change == null) {
      return false;
    }

    return branches.contains(change.getDest());
  }

  @Override
  public int getCost() {
    // match() is a single set lookup, however many branches the OR has.
    return 1;
  }

  @Override
  public String toString() {
    return "BranchSetIndexPredicate[" + name + "]" + super.toString();
  }

  private static List<Predicate<ChangeData>> getPredicates(Set<BranchNameKey> branches) {
    return branches.stream()
        .map(
            branchNameKey ->
                Predicate.and(
                    ChangePredicates.project(branchNameKey.project()),
                    ChangePredicates.ref(branchNameKey.branch())))
        .collect(Collectors.toList());
  }
}
