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

import com.google.gerrit.reviewdb.client.AccountGroup;

/** Summary information about an {@link AccountGroup}, for simple tabular displays. */
public class GroupInfo {
  protected AccountGroup.UUID uuid;
  protected String name;
  protected String description;
  protected String url;

  protected GroupInfo() {}

  /**
   * Create an anonymous group info, when only the id is known.
   *
   * <p>This constructor should only be a last-ditch effort, when the usual group lookup has failed
   * and a stale group id has been discovered in the data store.
   */
  public GroupInfo(final AccountGroup.UUID uuid) {
    this.uuid = uuid;
  }

  /**
   * Create a group description from a real data store record.
   *
   * @param a the data store record holding the specific group details.
   */
  public GroupInfo(GroupDescription.Basic a) {
    uuid = a.getGroupUUID();
    name = a.getName();
    url = a.getUrl();

    if (a instanceof GroupDescription.Internal) {
      AccountGroup group = ((GroupDescription.Internal) a).getAccountGroup();
      description = group.getDescription();
    }
  }

  /** @return the unique local id of the group */
  public AccountGroup.UUID getId() {
    return uuid;
  }

  /** @return the name of the group; null if not supplied */
  public String getName() {
    return name;
  }

  /** @return the description of the group; null if not supplied */
  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }
}
