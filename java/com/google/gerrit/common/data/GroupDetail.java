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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.util.Set;

public class GroupDetail {
  private Set<Account.Id> members;
  private Set<AccountGroup.UUID> includes;

  public GroupDetail(Set<Account.Id> members, Set<AccountGroup.UUID> includes) {
    this.members = members;
    this.includes = includes;
  }

  public Set<Account.Id> getMembers() {
    return members;
  }

  public Set<AccountGroup.UUID> getIncludes() {
    return includes;
  }
}
