// Copyright (C) 2026 Monty Taylor
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

package com.google.gerrit.extensions.common;

import java.util.List;

/** Direct Git parents resolved only against visible changes in the query response. */
public class IndexedDependenciesInfo {
  public String revision;
  public int patchSetNumber;
  public List<Parent> parents;

  /** The exact patch set referenced by a direct parent commit. */
  public static class Parent {
    public int changeNumber;
    public int patchSetNumber;
    public String commitId;
  }
}
