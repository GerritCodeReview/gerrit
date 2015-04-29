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

import java.util.concurrent.TimeUnit;

public interface MergeQueue {
  /**
   * Merges the changes and blocks until the merge is performed, if the merge is
   * currently possible.
   * <p>
   * If it is not possible due to lock congestion it will
   * not be scheduled for a later retry.
   *
   * @param changes The changes which should be merged.
   */
  void merge(ChangeSet changes);

  /**
   * Schedule changes for merge. The changes will be checked periodically and
   * merged eventually.
   *
   * @param changes The changes which should be merged eventually.
   */
  void schedule(ChangeSet changes);

  /**
   * This makes sure the changes are scheduled not earlier than the delay
   * specified from now.
   *
   * @param changes The changes which should be scheduled for a delay
   * @param delay The actual delay
   * @param delayUnit and its unit
   */
  void recheckAfter(ChangeSet changes, long delay, TimeUnit delayUnit);
}
