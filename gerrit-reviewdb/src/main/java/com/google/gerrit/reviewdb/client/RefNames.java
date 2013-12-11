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

package com.google.gerrit.reviewdb.client;

/** Constants and utilities for Gerrit-specific ref names. */
public class RefNames {
  public static final String REFS_CHANGES = "refs/changes/";

  /** Note tree listing commits we refuse {@code refs/meta/reject-commits} */
  public static final String REFS_REJECT_COMMITS = "refs/meta/reject-commits";

  /** Configuration settings for a project {@code refs/meta/config} */
  public static final String REFS_CONFIG = "refs/meta/config";

  /** Configurations of project-specific dashboards (canned search queries). */
  public static final String REFS_DASHBOARDS = "refs/meta/dashboards/";

  /**
   * Prefix applied to merge commit base nodes.
   * <p>
   * References in this directory should take the form
   * {@code refs/cache-automerge/xx/yyyy...} where xx is
   * the first two digits of the merge commit's object
   * name, and yyyyy... is the remaining 38. The reference
   * should point to a treeish that is the automatic merge
   * result of the merge commit's parents.
   */
  public static final String REFS_CACHE_AUTOMERGE = "refs/cache-automerge/";

  private RefNames() {
  }
}
