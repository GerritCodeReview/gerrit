// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import com.google.common.util.concurrent.Striped;
import com.google.gerrit.entities.BranchNameKey;
import com.google.inject.Singleton;
import java.util.concurrent.locks.Lock;

/**
 * Locks that serialize the pushes for review that look up the same change on the same branch.
 *
 * <p>A push looks up the existing change of each commit by its Change-Id (or by the commit, if it
 * has no Change-Id) in the index, and creates a new change when it finds none. Without a lock, two
 * concurrent pushes of the same commit both find no change, and both create one. The result is two
 * open changes with the same Change-Id on the same branch, which cannot be updated by a push
 * anymore, and which block the submission of each other when they share a commit.
 *
 * <p>The lock of a lookup is held until the push has created its changes and patch sets, and they
 * are indexed, so that the other push finds them.
 *
 * <p>The locks are local to this server. They don't protect against concurrent pushes to different
 * servers of a cluster.
 */
@Singleton
public class ChangeLookupLocks {
  private final Striped<Lock> locks = Striped.lock(1024);

  /**
   * Returns the lock of a lookup on a branch.
   *
   * @param dest the branch.
   * @param key the Change-Id, or the commit SHA-1 for a commit without a Change-Id.
   */
  Lock get(BranchNameKey dest, String key) {
    return locks.get(dest.project().get() + '\0' + dest.branch() + '\0' + key);
  }
}
