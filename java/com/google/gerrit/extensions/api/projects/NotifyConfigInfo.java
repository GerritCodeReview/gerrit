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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.common.GroupInfo;
import java.util.HashSet;
import java.util.Set;

public class NotifyConfigInfo implements Comparable<NotifyConfigInfo> {
  public enum Header {
    TO,
    CC,
    BCC
  }

  public enum NotifyType {
    // sort by name, except 'ALL' which should stay last
    ABANDONED_CHANGES,
    ALL_COMMENTS,
    NEW_CHANGES,
    NEW_PATCHSETS,
    SUBMITTED_CHANGES,

    ALL
  }

  public String name;
  public Boolean notifyNewChanges;
  public Boolean notifyNewPatchSets;
  public Boolean notifyAllComments;
  public Boolean notifySubmittedChanges;
  public Boolean notifyAbandonedChanges;

  public String filter;

  public Header header = Header.BCC;
  public Set<String> addresses = new HashSet<>();
  public Set<GroupInfo> groups = new HashSet<>();

  public void addEmail(String address) {
    addresses.add(address);
  }

  public void addGroup(GroupInfo groupInfo) {
    groups.add(groupInfo);
  }

  @Override
  public int compareTo(NotifyConfigInfo o) {
    return name.compareTo(o.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NotifyConfigInfo) {
      return compareTo((NotifyConfigInfo) obj) == 0;
    }
    return false;
  }

  @Override
  public String toString() {
    return "NotifyConfigInfo[" + name + " = " + addresses + " + " + groups + "]";
  }
}
