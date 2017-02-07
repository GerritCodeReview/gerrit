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

package com.google.gerrit.extensions.config;

import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Collection;

@ExtensionPoint
public interface ExternalIncludedIn {

  /**
   * Returns additional entries for IncludedInInfo as multimap where the key is the row title and
   * the the values are a list of systems that include the given commit (e.g. names of servers on
   * which this commit is deployed).
   *
   * <p>The tags and branches in which the commit is included are provided so that a RevWalk can be
   * avoided when a system runs a certain tag or branch.
   *
   * @param project the name of the project
   * @param commit the ID of the commit for which it should be checked if it is included
   * @param tags the tags that include the commit
   * @param branches the branches that include the commit
   * @return additional entries for IncludedInInfo
   */
  Multimap<String, String> getIncludedIn(
      String project, String commit, Collection<String> tags, Collection<String> branches);
}
