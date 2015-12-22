// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Change;
import com.google.inject.ImplementedBy;

/** Short lived change cache for reducing SQL traffic to the change table. */
@ImplementedBy(SearchingChangeCacheImpl.class)
public interface ChangeCache {

  /**
   * Return a Change by legacy id.
   *
   * @param legacyId legacy numeric id of the change.
   * @return Change object.
   */
  Change get(Change.Id legacyId);

  /**
   * Evict the in-memory cache value associated to a legacy id.
   *
   * @param legacyId legacy numeric id of the change to evict.
   */
  void evict(Change.Id legacyId);
}
