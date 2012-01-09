// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Change;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface MergeQueue {
  void merge(MergeOp.Factory mof, Change change);

  void merge(MergeOp.Factory mof, Set<Change> changes);

  void schedule(Change change);

  void schedule(Set<Change> group);

  /**
   * If the merge failed because of a missing dependency that might be submitted
   * in the near future, a caller may request a recheck to retry the merge after
   * the specified time. Note that rechecking only applies to non-group changes,
   * since for group changes we verify mergeability and fail immediately unless
   * the entire group is mergeable as a whole.
   */
  void recheckAfter(Change change, long delay, TimeUnit delayUnit);
}
