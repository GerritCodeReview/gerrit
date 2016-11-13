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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import java.util.List;

public class GroupDetail {
  public AccountInfoCache accounts;
  public AccountGroup group;
  public List<AccountGroupMember> members;
  public List<AccountGroupById> includes;
  public GroupReference ownerGroup;
  public boolean canModify;

  public GroupDetail() {}

  public void setAccounts(AccountInfoCache c) {
    accounts = c;
  }

  public void setGroup(AccountGroup g) {
    group = g;
  }

  public void setMembers(List<AccountGroupMember> m) {
    members = m;
  }

  public void setIncludes(List<AccountGroupById> i) {
    includes = i;
  }

  public void setOwnerGroup(GroupReference g) {
    ownerGroup = g;
  }

  public void setCanModify(final boolean canModify) {
    this.canModify = canModify;
  }
}
