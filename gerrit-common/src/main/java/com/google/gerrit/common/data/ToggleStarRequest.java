// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Change;

import java.util.HashSet;
import java.util.Set;

/** Request parameters to update the changes the user has toggled. */
public class ToggleStarRequest {
  protected Set<Change.Id> add;
  protected Set<Change.Id> remove;

  /**
   * Request an update to the change's star status.
   *
   * @param id unique id of the change, must not be null.
   * @param on true if the change should now be starred; false if it should now
   *        be not starred.
   */
  public void toggle(final Change.Id id, final boolean on) {
    if (on) {
      if (add == null) {
        add = new HashSet<Change.Id>();
      }
      add.add(id);
      if (remove != null) {
        remove.remove(id);
      }
    } else {
      if (remove == null) {
        remove = new HashSet<Change.Id>();
      }
      remove.add(id);
      if (add != null) {
        add.remove(id);
      }
    }
  }

  /** Get the set of changes which should have stars added; may be null. */
  public Set<Change.Id> getAddSet() {
    return add;
  }

  /** Get the set of changes which should have stars removed; may be null. */
  public Set<Change.Id> getRemoveSet() {
    return remove;
  }
}
