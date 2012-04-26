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

package com.google.gerrit.server.git;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.mail.Address;

import java.util.EnumSet;
import java.util.Set;

public class NotifyConfig {
  private EnumSet<NotifyType> types = EnumSet.allOf(NotifyType.class);
  private String filter;

  private Set<AccountGroup.UUID> groups = Sets.newHashSet();
  private Set<Address> addresses = Sets.newHashSet();

  public boolean isNotify(NotifyType type) {
    return types.contains(type);
  }

  public void setTypes(EnumSet<NotifyType> newTypes) {
    types = EnumSet.copyOf(newTypes);
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    if ("*".equals(filter)) {
      this.filter = null;
    } else {
      this.filter = Strings.emptyToNull(filter);
    }
  }

  public Set<AccountGroup.UUID> getGroups() {
    return groups;
  }

  public Set<Address> getAddresses() {
    return addresses;
  }

  public void addEmail(AccountGroup.UUID group) {
    groups.add(group);
  }

  public void addEmail(Address address) {
    addresses.add(address);
  }
}
