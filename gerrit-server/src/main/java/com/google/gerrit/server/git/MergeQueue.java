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

import com.google.gerrit.reviewdb.client.Change;

import java.util.concurrent.TimeUnit;

public interface MergeQueue {
  /**
   * Merges the changes if currently possible. If it is not possible it will be
   * scheduled and merged eventually.
   * @param changes
   */
  void merge(Iterable<Change> changes);

  /**
   * Schedule changes for merge. The changes will be checked periodically and
   * merged eventually.
   * @param changes
   */
  void schedule(Iterable<Change> changes);

  void recheckAfter(Iterable<Change> changes, long delay, TimeUnit delayUnit);
}
