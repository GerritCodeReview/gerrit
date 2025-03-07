// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class GroupInfo extends GroupBaseInfo {
  public String url;
  public GroupOptionsInfo options;

  // These fields are only supplied for internal groups.
  public String description;
  public Integer groupId;
  public String owner;
  public String ownerId;

  // TODO(issue-40014498): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp createdOn;

  public Boolean _moreGroups;

  // These fields are only supplied for internal groups, and only if requested.
  public List<AccountInfo> members;
  public List<GroupInfo> includes;

  // TODO(issue-40014498): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public Instant getCreatedOn() {
    return createdOn.toInstant();
  }

  // TODO(issue-40014498): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setCreatedOn(Instant when) {
    createdOn = Timestamp.from(when);
  }
}
