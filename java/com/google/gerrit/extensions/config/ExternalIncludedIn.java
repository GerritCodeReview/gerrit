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

import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Collection;

@ExtensionPoint
public interface ExternalIncludedIn {

  /**
   * Returns additional entries for IncludedInInfo as multimap where the key is the row title and
   * the values are a list of systems that include the given change or commit (e.g. names of
   * artifacts in which the change is included or names of servers on which this commit is
   * deployed).
   *
   * <p>The tags and branches in which the commit is included are provided so that a RevWalk can be
   * avoided when a system runs a certain tag or branch.
   *
   * @param project the name of the project
   * @param changeNumber the ID of the change that needs to be checked if it is included (can be
   *     null)
   * @param commit the ID of the commit, it can be used alongside or as an alternative to
   *     changeNumber to find additional included-ins
   * @param tags the tags that include the commit
   * @param branches the branches that include the commit
   * @return additional entries for IncludedInInfo
   */
  ListMultimap<String, String> getIncludedIn(
      String project,
      @Nullable Integer changeNumber,
      String commit,
      Collection<String> tags,
      Collection<String> branches);
}
