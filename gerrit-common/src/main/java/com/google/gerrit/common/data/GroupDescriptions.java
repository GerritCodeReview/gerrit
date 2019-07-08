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

/** Utility class for building GroupDescription objects. */
public class GroupDescriptions {

  @Nullable
  public static AccountGroup toAccountGroup(GroupDescription.Basic group) {
    if (group instanceof GroupDescription.Internal) {
      return ((GroupDescription.Internal) group).getAccountGroup();
    }
    return null;
  }

  public static GroupDescription.Internal forAccountGroup(final AccountGroup group) {
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
      public AccountGroup getAccountGroup() {
        return group;
      }

      @Override
      @Nullable
      public String getEmailAddress() {
        return null;
      }

      @Override
      @Nullable
      public String getUrl() {
        return "#" + PageLinks.toGroup(getGroupUUID());
      }
    };
  }

  private GroupDescriptions() {}
}
