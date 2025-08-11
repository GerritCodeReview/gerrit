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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.restapi.DefaultInput;
import java.util.Map;

/**
 * Input for creating a new branch.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#branch-input
 *
 * <p>This class holds the data needed to create a new branch, such as the revision to base the
 * branch on and whether to create an empty commit.
 *
 * <p>The following properties can be set:
 *
 * <ul>
 *   <li>{@code revision}: The base revision of the new branch. If not set and create_empty_commit
 *       is true the branch is created with an empty initial commit. If not set and
 *       create_empty_commit is false or unset HEAD will be used as base revision.
 *   <li>{@code createEmptyCommit}: Whether the branch should be created with an empty initial
 *       commit. It is not possible to create a branch on a project with no commits. Cannot be used
 *       in combination with setting a revision. Can be used to review the initial content of a
 *       branch (create the branch with an empty initial commit, make a second commit with the
 *       initial content, e.g. by merging in another branch, and push the commit for review)..
 *   <li>{@code ref}: The name of the branch. The prefix refs/heads/ can be omitted. If set, must
 *       match the branch ID in the URL.
 *   <li>{@code sourceRef}: The full name of the source ref where {@code revision} can be found.
 *       This ref should be visible to the caller. Used when {@code revision} is not a ref name in
 *       order to check reachability from a specific ref. If not set, then all visible refs under
 *       refs/heads/ and refs/tags/ are searched (see {@code CreateRefControl#checkCreateCommit} for
 *       details).
 *   <li>{@code validationOptions}: Map with key-value pairs that are forwarded as options to the
 *       ref operation validation listeners (e.g. can be used to skip certain validations). Which
 *       validation options are supported depends on the installed ref operation validation
 *       listeners. Gerrit core doesn’t support any validation options, but ref operation validation
 *       listeners that are implemented in plugins may. Please refer to the documentation of the
 *       installed plugins to learn whether they support validation options. Unknown validation
 *       options are silently ignored.
 * </ul>
 */
public class BranchInput {
  @DefaultInput public String revision;
  public boolean createEmptyCommit;
  public String ref;
  public String sourceRef;
  public Map<String, String> validationOptions;
}
