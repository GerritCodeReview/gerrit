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

import com.google.gerrit.reviewdb.client.Project;

/** Manages replication to other nodes. */
public interface ReplicationQueue {
  /** Is replication to one or more other destinations configured? */
  boolean isEnabled();

  /**
   * Schedule a full replication for a single project.
   * <p>
   * All remote URLs are checked to verify the are current with regards to the
   * local project state. If not, they are updated by pushing new refs, updating
   * existing ones which don't match, and deleting stale refs which have been
   * removed from the local repository.
   *
   * @param project identity of the project to replicate.
   * @param urlMatch substring that must appear in a URI to support replication.
   */
  void scheduleFullSync(Project.NameKey project, String urlMatch);

  /**
   * Schedule a full replication for a single project with callback support.
   * <p>
   * All remote URLs are checked to verify the are current with regards to the
   * local project state. If not, they are updated by pushing new refs, updating
   * existing ones which don't match, and deleting stale refs which have been
   * removed from the local repository, after finishing replication, the callback
   * functions will be executed.
   *
   * @param project identity of the project to replicate.
   * @param urlMatch substring that must appear in a URI to support replication.
   * @param callback operations that need to do after replication.
   */
  void scheduleFullSync(Project.NameKey project, String urlMatch,
      ReplicationCallback callback);

  /**
   * Schedule update of a single ref.
   * <p>
   * This method automatically tries to batch together multiple requests in the
   * same project, to take advantage of Git's native ability to update multiple
   * refs during a single push operation.
   *
   * @param project identity of the project to replicate.
   * @param ref unique name of the ref; must start with {@code refs/}.
   */
  void scheduleUpdate(Project.NameKey project, String ref);

  /**
   * Schedule update of a single ref with callback support.
   * <p>
   * This method automatically tries to batch together multiple requests in the
   * same project, to take advantage of Git's native ability to update multiple
   * refs during a single push operation, after finishing replication, the callback
   * functions will be executed.
   *
   * @param project identity of the project to replicate.
   * @param ref unique name of the ref; must start with {@code refs/}.
   * @param callback operations that need to do after replication
   */
  void scheduleUpdate(Project.NameKey project, String ref,
      ReplicationCallback callback);

  /**
   * Create new empty project at the remote sites.
   * <p>
   * When a new project has been created locally call this method to make sure
   * that the project will be created at the remote sites as well.
   *
   * @param project of the project to be created.
   * @param head name HEAD should point at (must be {@code refs/heads/...}).
   */
  void replicateNewProject(Project.NameKey project, String head);
}
