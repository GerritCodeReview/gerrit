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

import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetDetail implements RestReadView<GroupResource> {
  private final GroupJson json;

  @Inject
  GetDetail(GroupJson json) {
    this.json = json.addOption(ListGroupsOption.MEMBERS).addOption(ListGroupsOption.INCLUDES);
  }

  @Override
  public GroupInfo apply(GroupResource rsrc) throws OrmException {
    return json.format(rsrc);
  }
}
