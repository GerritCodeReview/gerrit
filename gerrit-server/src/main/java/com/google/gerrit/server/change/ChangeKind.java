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

package com.google.gerrit.server.change;

/** Operation performed by a change relative to its parent. */
public enum ChangeKind {
  /** Nontrivial content changes. */
  REWORK,

  /** Conflict-free merge between the new parent and the prior patch set. */
  TRIVIAL_REBASE,

  /** Same tree and same parents. */
  NO_CODE_CHANGE;
}
