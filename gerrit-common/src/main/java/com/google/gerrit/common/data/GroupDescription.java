// Copyright (C) 2012 The Android Open Source Project
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

/**
 * Group methods exposed by the GroupBackend.
 */
public class GroupDescription {
  /**
   * The Basic information required to be exposed by any Group.
   */
  public interface Basic {
    /** @return the non-null UUID of the group. */
    AccountGroup.UUID getGroupUUID();

    /** @return the non-null name of the group. */
    String getName();

    /** @return whether the group is visible to all accounts. */
    boolean isVisibleToAll();
  }

  /**
   * The extended information exposed by internal groups backed by an
   * AccountGroup.
   */
  public interface Internal extends Basic {
    /** @return the backing AccountGroup. */
    AccountGroup getAccountGroup();
  }

  private GroupDescription() {
  }
}
