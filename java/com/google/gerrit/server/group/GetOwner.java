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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetOwner implements RestReadView<GroupResource> {

  private final GroupControl.Factory controlFactory;
  private final GroupJson json;

  @Inject
  GetOwner(GroupControl.Factory controlFactory, GroupJson json) {
    this.controlFactory = controlFactory;
    this.json = json;
  }

  @Override
  public GroupInfo apply(GroupResource resource)
      throws MethodNotAllowedException, ResourceNotFoundException, OrmException {
    AccountGroup group = resource.toAccountGroup();
    if (group == null) {
      throw new MethodNotAllowedException();
    }
    try {
      GroupControl c = controlFactory.validateFor(group.getOwnerGroupUUID());
      return json.format(c.getGroup());
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException();
    }
  }
}
