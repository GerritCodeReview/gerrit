// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class SubgroupResource extends GroupResource {
  public static final TypeLiteral<RestView<SubgroupResource>> SUBGROUP_KIND =
      new TypeLiteral<RestView<SubgroupResource>>() {};

  private final GroupDescription.Basic member;

  public SubgroupResource(GroupResource group, GroupDescription.Basic member) {
    super(group);
    this.member = member;
  }

  public AccountGroup.UUID getMember() {
    return getMemberDescription().getGroupUUID();
  }

  public GroupDescription.Basic getMemberDescription() {
    return member;
  }
}
