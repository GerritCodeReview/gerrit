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
 * Interface for {@link BatchUpdate} operations that touch a change.
 *
 * <p>Each operation has {@link #updateChange(ChangeContext)} called once the change is read in a
 * transaction. Ops are associated with updates via {@link
 * BatchUpdate#addOp(com.google.gerrit.reviewdb.client.Change.Id, BatchUpdateOp)}.
 *
 * <p>Usually, a single {@code BatchUpdateOp} instance is only associated with a single change, i.e.
 * {@code addOp} is only called once with that instance. This allows an instance to communicate
 * between phases by storing data in private fields.
 */
public interface BatchUpdateOp extends RepoOnlyOp {
  /**
   * Override this method to modify a change.
   *
   * @param ctx context
   * @return whether anything was changed that might require a write to the metadata storage.
   */
  default boolean updateChange(ChangeContext ctx) throws Exception {
    return false;
  }
}
