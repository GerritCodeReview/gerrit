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

package com.google.gerrit.server.api.groups;

import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.group.GetDetail;
import com.google.gerrit.server.group.GetGroup;
import com.google.gerrit.server.group.GroupResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

class GroupApiImpl implements GroupApi {
  interface Factory {
    GroupApiImpl create(GroupResource rsrc);
  }

  private final GetGroup getGroup;
  private final GetDetail getDetail;
  private final GroupResource rsrc;

  @AssistedInject
  GroupApiImpl(
      GetGroup getGroup,
      GetDetail getDetail,
      @Assisted GroupResource rsrc) {
    this.getGroup = getGroup;
    this.getDetail = getDetail;
    this.rsrc = rsrc;
  }

  @Override
  public GroupInfo get() throws RestApiException {
    try {
      return getGroup.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve group", e);
    }
  }

  @Override
  public GroupInfo detail() throws RestApiException {
    try {
      return getDetail.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve group", e);
    }
  }

}
