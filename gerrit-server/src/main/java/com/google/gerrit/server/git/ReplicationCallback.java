// Copyright (C) 2012 The Android Open Source Project
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

import org.eclipse.jgit.transport.URIish;

/** A callback for running after replication */
public interface ReplicationCallback {

  public static enum ReplicationStatus {
    /**
     * The ref is not replicated to slave.
     */
    FAILED,

    /**
     * The ref is not configured to be replicated.
     */
    NOT_ATTEMPTED,

    /**
     * ref was successfully replicated.
     */
    SUCCEEDED;
  }

  /**
   * After finishing replication to one node, run this method once.
   * <p>
   * When several nodes are configured to replicate, it is possible to execute
   * a task for each node after replicating to it.
   *
   * @param uri the node to which the replication has finished.
   * @param status replication result.
   * @param finishedNodes the number of nodes to which replication has finished currently.
   * @param totalNodes the number of nodes that need to be replicated in all.
   */
  void onOneNodeReplicated(URIish uri, ReplicationStatus status, int finishedNodes,
      int totalNodes);
}
