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

package com.google.gerrit.extensions.config;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Collection;

@ExtensionPoint
public class FilterIncludedIn {

  /**
   * Returns a filtered list of branches.
   *
   * @param project the name of the project
   * @param commit the ID of the commit for which it should be checked if it is included
   * @param branches the branches that include the commit
   * @return filtered list of branches
   */
  public ImmutableSortedSet<String> filterBranches(
      String project, String commit, Collection<String> branches) {
    return null;
  }

  /**
   * Returns a filtered list of tags.
   *
   * @param project the name of the project
   * @param commit the ID of the commit for which it should be checked if it is included
   * @param tags the tags that include the commit
   * @return filtered list of tags
   */
  public ImmutableSortedSet<String> filterTags(
      String project, String commit, Collection<String> tags) {
    return null;
  }
}
