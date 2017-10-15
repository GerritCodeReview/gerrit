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

import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Context for performing the {@link BatchUpdateOp#updateRepo} phase. */
public interface RepoContext extends Context {
  /**
   * @return inserter for writing to the repo. Callers should not flush; the walk returned by {@link
   *     #getRevWalk()} is able to read back objects inserted by this inserter without flushing
   *     first.
   * @throws IOException if an error occurred opening the repo.
   */
  ObjectInserter getInserter() throws IOException;

  /**
   * Add a command to the pending list of commands.
   *
   * <p>Adding commands to the {@code RepoContext} is the only way of updating refs in the
   * repository from a {@link BatchUpdateOp}.
   *
   * @param cmd ref update command.
   * @throws IOException if an error occurred opening the repo.
   */
  void addRefUpdate(ReceiveCommand cmd) throws IOException;

  /**
   * Add a command to the pending list of commands.
   *
   * <p>Adding commands to the {@code RepoContext} is the only way of updating refs in the
   * repository from a {@link BatchUpdateOp}.
   *
   * @param oldId the old object ID; must not be null. Use {@link ObjectId#zeroId()} for ref
   *     creation.
   * @param newId the new object ID; must not be null. Use {@link ObjectId#zeroId()} for ref
   *     deletion.
   * @param refName the ref name.
   * @throws IOException if an error occurred opening the repo.
   */
  default void addRefUpdate(ObjectId oldId, ObjectId newId, String refName) throws IOException {
    addRefUpdate(new ReceiveCommand(oldId, newId, refName));
  }
}
