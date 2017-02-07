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
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.mail.Address;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class NotifyConfig implements Comparable<NotifyConfig> {
  public enum Header {
    TO,
    CC,
    BCC
  }

  private String name;
  private EnumSet<NotifyType> types = EnumSet.of(NotifyType.ALL);
  private String filter;

  private Header header;
  private Set<GroupReference> groups = new HashSet<>();
  private Set<Address> addresses = new HashSet<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isNotify(NotifyType type) {
    return types.contains(type) || types.contains(NotifyType.ALL);
  }

  public EnumSet<NotifyType> getNotify() {
    return types;
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

  public Header getHeader() {
    return header;
  }

  public void setHeader(Header hdr) {
    header = hdr;
  }

  public Set<GroupReference> getGroups() {
    return groups;
  }

  public Set<Address> getAddresses() {
    return addresses;
  }

  public void addEmail(GroupReference group) {
    groups.add(group);
  }

  public void addEmail(Address address) {
    addresses.add(address);
  }

  @Override
  public int compareTo(NotifyConfig o) {
    return name.compareTo(o.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NotifyConfig) {
      return compareTo((NotifyConfig) obj) == 0;
    }
    return false;
  }

  @Override
  public String toString() {
    return "NotifyConfig[" + name + " = " + addresses + " + " + groups + "]";
  }
}
