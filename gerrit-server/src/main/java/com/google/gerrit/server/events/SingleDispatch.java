// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gwtorm.server.OrmException;

/** Base class to dispatch stream events to a single fire(Event) method.
 *
 *  This is useful when routing events without needing to do
 *  visibility checks, perhaps to another host, or perhaps
 *  to storage.
 */
public abstract class SingleDispatch implements EventDispatcher {
  @Override
  public void fireEventForUnrestrictedListeners(Event event) {
    fire(event);
  }

  @Override
  public void fireEvent(Change change, ChangeEvent event, ReviewDb db)
      throws OrmException {
    fire(event);
  }

  @Override
  public void fireEvent(Branch.NameKey branchName, RefEvent event) {
    fire(event);
  }

  @Override
  public void fireEvent(Event event, ReviewDb db)
      throws OrmException {
    fire(event);
  }

  public abstract void fire(Event event);
}
