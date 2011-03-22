// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccountGroup;

/** Summary information about an {@link AccountGroup}, for simple tabular displays. */
public class GroupInfo {
  protected AccountGroup.Id id;
  protected String name;
  protected String description;

  protected GroupInfo() {
  }

  /**
   * Create an anonymous group info, when only the id is known.
   * <p>
   * This constructor should only be a last-ditch effort, when the usual group
   * lookup has failed and a stale group id has been discovered in the data
   * store.
   */
  public GroupInfo(final AccountGroup.Id id) {
    this.id = id;
  }

  /**
   * Create a group description from a real data store record.
   *
   * @param a the data store record holding the specific group details.
   */
  public GroupInfo(final AccountGroup a) {
    id = a.getId();
    name = a.getName();
    description = a.getDescription();
  }

  /** @return the unique local id of the group */
  public AccountGroup.Id getId() {
    return id;
  }

  /** @return the name of the group; null if not supplied */
  public String getName() {
    return name;
  }

  /** @return the description of the group; null if not supplied */
  public String getDescription() {
    return description;
  }
}
