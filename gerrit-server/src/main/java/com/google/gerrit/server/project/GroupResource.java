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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupControl;
import com.google.inject.TypeLiteral;

public class GroupResource implements RestResource {
  public static final TypeLiteral<RestView<GroupResource>> GROUP_KIND =
      new TypeLiteral<RestView<GroupResource>>() {};

  private final GroupControl control;

  GroupResource(GroupControl control) {
    this.control = control;
  }

  public String getName() {
    return control.getGroup().getName();
  }

  public AccountGroup.UUID getGroupUUID() {
    return control.getGroup().getGroupUUID();
  }

  public GroupControl getControl() {
    return control;
  }
}
