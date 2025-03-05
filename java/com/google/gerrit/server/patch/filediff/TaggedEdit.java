// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch.filediff;

import static java.util.Objects.requireNonNull;

/**
 * An entity class encapsulating a JGit {@link Edit} along with extra attributes (e.g. identifying a
 * rebase edit).
 */
public record TaggedEdit(Edit edit, boolean dueToRebase) {
  public TaggedEdit {
    requireNonNull(edit, "edit");
  }

  public static TaggedEdit create(Edit edit, boolean dueToRebase) {
    return new TaggedEdit(edit, dueToRebase);
  }

  public org.eclipse.jgit.diff.Edit jgitEdit() {
    return Edit.toJGitEdit(edit());
  }
}
