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

/**
 * Interface for listening during batch update execution.
 *
 * <p>When used during execution of multiple batch updates, the {@code after*} methods are called
 * after that phase has been completed for <em>all</em> updates.
 */
public interface BatchUpdateListener {
  public static final BatchUpdateListener NONE = new BatchUpdateListener() {};

  /** Called after updating all repositories and flushing objects but before updating any refs. */
  default void afterUpdateRepos() throws Exception {}

  /** Called after updating all refs. */
  default void afterUpdateRefs() throws Exception {}

  /** Called after updating all changes. */
  default void afterUpdateChanges() throws Exception {}
}
