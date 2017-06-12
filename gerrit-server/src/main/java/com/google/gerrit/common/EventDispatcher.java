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

package com.google.gerrit.common;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefEvent;
import com.google.gwtorm.server.OrmException;

/** Interface for posting (dispatching) Events */
public interface EventDispatcher {
  /**
   * Post a stream event that is related to a change
   *
   * @param change The change that the event is related to
   * @param event The event to post
   * @throws OrmException on failure to post the event due to DB error
   */
  void postEvent(Change change, ChangeEvent event) throws OrmException;

  /**
   * Post a stream event that is related to a branch
   *
   * @param branchName The branch that the event is related to
   * @param event The event to post
   */
  void postEvent(Branch.NameKey branchName, RefEvent event);

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
   * @throws OrmException on failure to post the event due to DB error
   */
  void postEvent(Event event) throws OrmException;
}
