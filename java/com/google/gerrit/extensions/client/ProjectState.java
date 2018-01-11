// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

public enum ProjectState {
  /** Permits reading project state and contents as well as mutating data. */
  ACTIVE(true, true),
  /** Permits reading project state and contents. Does not permit any modifications. */
  READ_ONLY(true, false),
  /**
   * Hides the project as if it was deleted, but makes requests fail with an error message that
   * reveals the project's existence.
   */
  HIDDEN(false, false);

  private final boolean permitsRead;
  private final boolean permitsWrite;

  ProjectState(boolean permitsRead, boolean permitsWrite) {
    this.permitsRead = permitsRead;
    this.permitsWrite = permitsWrite;
  }

  public boolean permitsRead() {
    return permitsRead;
  }

  public boolean permitsWrite() {
    return permitsWrite;
  }
}
