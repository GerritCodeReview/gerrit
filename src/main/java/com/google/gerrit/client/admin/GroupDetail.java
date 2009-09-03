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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;

import java.util.List;

public class GroupDetail {
  protected AccountInfoCache accounts;
  protected AccountGroup group;
  protected List<AccountGroupMember> members;
  protected AccountGroup ownerGroup;
  protected List<RealmProperty> realmProperties;

  public GroupDetail() {
  }

  public void setAccounts(AccountInfoCache c) {
    accounts = c;
  }

  public void setGroup(AccountGroup g) {
    group = g;
  }

  public void setMembers(List<AccountGroupMember> m) {
    members = m;
  }

  public void setOwnerGroup(AccountGroup g) {
    ownerGroup = g;
  }

  public void setRealmProperties(List<RealmProperty> p) {
    realmProperties = p;
  }

  public static class RealmProperty {
    protected String name;
    protected String value;

    protected RealmProperty() {
    }

    public RealmProperty(final String n, final String v) {
      name = n;
      value = v;
    }

    public String getName() {
      return name;
    }
  }
}
