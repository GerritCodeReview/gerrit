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

package com.google.gerrit.server.query.change;

import java.util.List;

/** Versioned index snapshot of the current revision's ordered direct parent object IDs. */
public record CurrentRevisionParents(
    int version, int patchSetNumber, String commitId, List<String> parentIds) {
  public static final int VERSION = 1;

  public CurrentRevisionParents {
    // Preserve missing fields when reading a snapshot written with a different record version.
    parentIds = parentIds == null ? null : List.copyOf(parentIds);
  }
}
