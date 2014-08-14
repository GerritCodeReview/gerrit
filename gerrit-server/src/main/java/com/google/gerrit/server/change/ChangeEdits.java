// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Optional;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
class ChangeEdits implements
    ChildCollection<ChangeResource, ChangeEditResource> {
  private final DynamicMap<RestView<ChangeEditResource>> views;
  private final Detail detail;

  @Inject
  ChangeEdits(DynamicMap<RestView<ChangeEditResource>> views,
      Detail detail) {
    this.views = views;
    this.detail = detail;
  }

  @Override
  public DynamicMap<RestView<ChangeEditResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() {
    return detail;
  }

  @Override
  public ChangeEditResource parse(ChangeResource change, IdString id) {
    throw new IllegalStateException("not yet implemented");
  }

  @Singleton
  static class Detail implements RestReadView<ChangeResource> {
    private final ChangeEditUtil editUtil;
    private final ChangeEditJson editJson;

    @Inject
    Detail(ChangeEditJson editJson,
        ChangeEditUtil editUtil) {
      this.editJson = editJson;
      this.editUtil = editUtil;
    }

    @Override
    public Response<EditInfo> apply(ChangeResource rsrc) throws AuthException,
        IOException, NoSuchChangeException, InvalidChangeOperationException,
        ResourceNotFoundException {
      Optional<ChangeEdit> edit = editUtil.byChange(rsrc.getChange());
      if (edit.isPresent()) {
        return Response.ok(editJson.toEditInfo(edit.get()));
      }
      return Response.none();
    }
  }
}
