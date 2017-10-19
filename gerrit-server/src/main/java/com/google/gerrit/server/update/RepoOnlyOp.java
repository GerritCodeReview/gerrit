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
 * Base interface for operations performed as part of a {@link BatchUpdate}.
 *
 * <p>Operations that implement this type only touch the repository; they cannot touch change
 * storage, nor are they even associated with a change ID. To modify a change, implement {@link
 * BatchUpdateOp} instead.
 */
public interface RepoOnlyOp {
  /**
   * Override this method to update the repo.
   *
   * @param ctx context
   */
  default void updateRepo(RepoContext ctx) throws Exception {}

  /**
   * Override this method to do something after the update e.g. send email or run hooks
   *
   * @param ctx context
   */
  // TODO(dborowitz): Support async operations?
  default void postUpdate(Context ctx) throws Exception {}
}
