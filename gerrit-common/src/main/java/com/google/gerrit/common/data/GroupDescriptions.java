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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountGroup;
import java.sql.Timestamp;

/** Utility class for building GroupDescription objects. */
public class GroupDescriptions {

  public static GroupDescription.Internal forAccountGroup(AccountGroup group) {
    return new GroupDescription.Internal() {
      @Override
      public AccountGroup.UUID getGroupUUID() {
        return group.getGroupUUID();
      }

      @Override
      public String getName() {
        return group.getName();
      }

      @Override
      @Nullable
      public String getEmailAddress() {
        return null;
      }

      @Override
      public String getUrl() {
        return "#" + PageLinks.toGroup(getGroupUUID());
      }

      @Override
      public AccountGroup.Id getId() {
        return group.getId();
      }

      @Override
      @Nullable
      public String getDescription() {
        return group.getDescription();
      }

      @Override
      public AccountGroup.UUID getOwnerGroupUUID() {
        return group.getOwnerGroupUUID();
      }

      @Override
      public boolean isVisibleToAll() {
        return group.isVisibleToAll();
      }

      @Override
      public Timestamp getCreatedOn() {
        return group.getCreatedOn();
      }
    };
  }

  private GroupDescriptions() {}
}
