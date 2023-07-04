// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.index;

public enum PaginationType {
  /** Index queries are restarted at a non-zero offset to obtain the next set of results */
  OFFSET,

  /**
   * Index queries are restarted using a search-after object. Supported index backends can provide
   * their custom implementations for search-after.
   *
   * <p>For example, Lucene implementation uses the last doc from the previous search as
   * search-after object and uses the IndexSearcher.searchAfter API to get the next set of results.
   */
  SEARCH_AFTER,

  /** Index queries are executed returning all results, without internal pagination. */
  NONE
}
