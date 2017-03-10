// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.update;

/** Order of execution of the various phases of a {@link BatchUpdate}. */
public enum Order {
  /**
   * Update the repository and execute all ref updates before touching the database.
   *
   * <p>The default and most common, as Gerrit does not behave well when a patch set has no
   * corresponding ref in the repo.
   */
  REPO_BEFORE_DB,

  /**
   * Update the database before touching the repository.
   *
   * <p>Generally only used when deleting patch sets, which should be deleted first from the
   * database (for the same reason as above.)
   */
  DB_BEFORE_REPO;
}
