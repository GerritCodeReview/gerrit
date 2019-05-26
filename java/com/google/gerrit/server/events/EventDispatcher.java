// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.permissions.PermissionBackendException;

/** Interface for posting (dispatching) Events */
public interface EventDispatcher {
  /**
   * Post a stream event that is related to a change
   *
   * @param change The change that the event is related to
   * @param event The event to post
   * @throws PermissionBackendException on failure of permission checks
   */
  void postEvent(Change change, ChangeEvent event) throws PermissionBackendException;

  /**
   * Post a stream event that is related to a branch
   *
   * @param branchName The branch that the event is related to
   * @param event The event to post
   * @throws PermissionBackendException on failure of permission checks
   */
  void postEvent(BranchNameKey branchName, RefEvent event) throws PermissionBackendException;

  /**
   * Post a stream event that is related to a project.
   *
   * @param projectName The project that the event is related to.
   * @param event The event to post.
   */
  void postEvent(Project.NameKey projectName, ProjectEvent event);

  /**
   * Post a stream event generically.
   *
   * <p>If you are creating a RefEvent or ChangeEvent from scratch, it is more efficient to use the
   * specific postEvent methods for those use cases.
   *
   * @param event The event to post.
   * @throws PermissionBackendException on failure of permission checks
   */
  void postEvent(Event event) throws PermissionBackendException;
}
