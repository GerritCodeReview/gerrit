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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.Event;
import com.google.gwtorm.server.OrmException;


/** Interface for posting (dispatching) Events */
public interface EventDispatcher {
  /**
   * Post a stream event that is related to a change
   *
   * @param change The change that the event is related to
   * @param event The event to post
   * @param db The database
   * @throws OrmException
   */
  void postEvent(Change change, Event event, ReviewDb db)
      throws OrmException;

  /**
   * Post a stream event that is related to a branch
   *
   * @param branchName The branch that the event is related to
   * @param event The event to post
   */
  void postEvent(Branch.NameKey branchName, Event event);
}
