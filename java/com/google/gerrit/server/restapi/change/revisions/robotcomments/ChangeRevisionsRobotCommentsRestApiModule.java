// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change.revisions.robotcomments;

import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;
import static com.google.gerrit.server.change.RobotCommentResource.ROBOT_COMMENT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class ChangeRevisionsRobotCommentsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(RobotComments.class);

    DynamicMap.mapOf(binder(), ROBOT_COMMENT_KIND);

    child(REVISION_KIND, "robotcomments").to(RobotComments.class);
    get(ROBOT_COMMENT_KIND).to(GetRobotComment.class);
  }
}
