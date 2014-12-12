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

package com.google.gerrit.common;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gwtorm.server.OrmException;


/** Interface for dispatching Events ready to be fired */
public interface EventDispatcher {
  public void fireEventForUnrestrictedListeners(final Event event);

  public void fireEvent(final Change change, final ChangeEvent event,
      final ReviewDb db) throws OrmException;

  public void fireEvent(Branch.NameKey branchName, final RefEvent event);

  public void fireEvent(final Event event, final ReviewDb db)
      throws OrmException;
}
