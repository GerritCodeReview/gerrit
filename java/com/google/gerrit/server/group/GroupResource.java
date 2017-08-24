// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupControl;
import com.google.inject.TypeLiteral;

public class GroupResource implements RestResource {
  public static final TypeLiteral<RestView<GroupResource>> GROUP_KIND =
      new TypeLiteral<RestView<GroupResource>>() {};

  private final GroupControl control;

  public GroupResource(GroupControl control) {
    this.control = control;
  }

  GroupResource(GroupResource rsrc) {
    this.control = rsrc.getControl();
  }

  public GroupDescription.Basic getGroup() {
    return control.getGroup();
  }

  public String getName() {
    return getGroup().getName();
  }

  public AccountGroup.UUID getGroupUUID() {
    return getGroup().getGroupUUID();
  }

  public AccountGroup toAccountGroup() {
    return GroupDescriptions.toAccountGroup(getGroup());
  }

  public GroupControl getControl() {
    return control;
  }
}
