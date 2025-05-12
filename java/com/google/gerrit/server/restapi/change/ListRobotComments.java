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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListRobotComments implements RestReadView<RevisionResource> {

  @Inject
  ListRobotComments() {}

  @Override
  public Response<Map<String, List<RobotCommentInfo>>> apply(RevisionResource rsrc)
      throws ResourceNotFoundException {
    throw new ResourceNotFoundException("robot comments unsupported");
  }

  public ImmutableList<RobotCommentInfo> getComments(RevisionResource rsrc) {
    return ImmutableList.of();
  }
}
