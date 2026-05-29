// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.RobotCommentResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Deprecated
@Singleton
public class RobotComments implements ChildCollection<RevisionResource, RobotCommentResource> {
  private final DynamicMap<RestView<RobotCommentResource>> views;
  private final ListRobotComments list;

  @Inject
  RobotComments(DynamicMap<RestView<RobotCommentResource>> views, ListRobotComments list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<RobotCommentResource>> views() {
    return views;
  }

  @Override
  public ListRobotComments list() {
    return list;
  }

  @Override
  public RobotCommentResource parse(RevisionResource rev, IdString id)
      throws ResourceNotFoundException {
    throw new ResourceNotFoundException("robot comments unsupported");
  }
}
