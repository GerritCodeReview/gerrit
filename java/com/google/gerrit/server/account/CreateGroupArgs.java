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
// limitations under the License

package com.google.gerrit.server.account;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.util.Collection;

public class CreateGroupArgs {
  private AccountGroup.NameKey groupName;
  public AccountGroup.UUID uuid;
  public String groupDescription;
  public boolean visibleToAll;
  public AccountGroup.UUID ownerGroupUuid;
  public Collection<? extends Account.Id> initialMembers;

  public AccountGroup.NameKey getGroup() {
    return groupName;
  }

  @Nullable
  public String getGroupName() {
    return groupName != null ? groupName.get() : null;
  }

  public void setGroupName(String n) {
    groupName = n != null ? AccountGroup.nameKey(n.trim()) : null;
  }
}
