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

package com.google.gerrit.extensions.common;

/** Type of modification made to the file path. */
public enum ChangeType {
  /** Path is being created/introduced by this patch. */
  ADDED,

  /** Path already exists, and has updated content. */
  MODIFIED,

  /** Path existed, but is being removed by this patch. */
  DELETED,

  /** Path existed but was moved. */
  RENAMED,

  /** Path was copied from source. */
  COPIED,

  /** Sufficient amount of content changed to claim the file was rewritten. */
  REWRITE
}
