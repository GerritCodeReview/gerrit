// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.entities.Project;
import java.util.function.Predicate;
import org.eclipse.jgit.revwalk.RevCommit;

public interface FilterIncludedIn {

  /**
   * Returns a predicate for filtering branches.
   *
   * @param project the name of the project
   * @param commit the commit for which it should do the filtering
   * @return A predicate that returns true if the branch should be included in the result
   */
  public default Predicate<String> getBranchFilter(Project.NameKey project, RevCommit commit) {
    return branch -> true;
  }

  /**
   * Returns a predicate for filtering tags.
   *
   * @param project the name of the project
   * @param commit the commit for which it should do the filtering
   * @return A predicate that returns true if the tag should be included in the result
   */
  public default Predicate<String> getTagFilter(Project.NameKey project, RevCommit commit) {
    return tag -> true;
  }
}
