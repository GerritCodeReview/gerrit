// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.api.projects.NotifyConfigInfo.Header;
import java.util.HashSet;
import java.util.Set;

public class NotifyConfigInput implements Comparable<NotifyConfigInput> {

  public String name;
  public Boolean notifyNewChanges;
  public Boolean notifyNewPatchSets;
  public Boolean notifyAllComments;
  public Boolean notifySubmittedChanges;
  public Boolean notifyAbandonedChanges;
  public String filter;
  public Header header = Header.BCC;
  public Set<String> emails = new HashSet<>();
  public Set<String> groupIds = new HashSet<>();

  public void addEmail(String email) {
    emails.add(email);
  }

  public void addGroup(String groupId) {
    groupIds.add(groupId);
  }

  @Override
  public int compareTo(NotifyConfigInput o) {
    return name.compareTo(o.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NotifyConfigInput) {
      return compareTo((NotifyConfigInput) obj) == 0;
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("emails", emails)
        .add("group ids", groupIds)
        .add("header", header)
        .add("filter", filter)
        .toString();
  }
}
