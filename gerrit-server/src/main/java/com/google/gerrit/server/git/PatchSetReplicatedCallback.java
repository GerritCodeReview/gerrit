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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callback for patchset replication, this callback will be executed
 * after patchset replication has completed.
 */
public class PatchSetReplicatedCallback implements ReplicationCallback {
  private static final Logger log =
      LoggerFactory.getLogger(PatchSetReplicatedCallback.class);

  private final Change change;
  private final PatchSet patchSet;
  private final ReviewDb db;
  private final ChangeHooks hooks;

  public PatchSetReplicatedCallback(Change change, PatchSet patchSet,
      ReviewDb db, ChangeHooks hooks) {
    this.change = change;
    this.patchSet = patchSet;
    this.db = db;
    this.hooks = hooks;
  }

  @Override
  public void onOneNodeReplicated(URIish uri, ReplicationStatus status,
      int finishedNodes, int totalNodes) {
    try {
      hooks.doPatchsetReplicatedHook(uri, status, finishedNodes, totalNodes,
          change, patchSet, db);
    } catch (OrmException e) {
      log.error("Failed to execute replication hook.", e);
    }
  }
}
