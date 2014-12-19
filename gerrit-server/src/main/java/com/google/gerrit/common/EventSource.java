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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;

/** Distributes Events to ChangeListeners.  Register listeners here. */
public interface EventSource {
  public void addEventListener(EventListener listener, CurrentUser user);

  public void addEventListener(EventListener listener, CurrentUser user,
      long sequenceId, ReviewDb db) throws OrmException;

  public void removeEventListener(EventListener listener);
}
