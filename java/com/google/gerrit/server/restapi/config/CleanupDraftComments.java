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

package com.google.gerrit.server.restapi.config;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.DraftCommentsCleanupRunner;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@RequiresCapability(GlobalCapability.MAINTAIN_SERVER)
@Singleton
public class CleanupDraftComments implements RestModifyView<ConfigResource, Input> {

  private final WorkQueue workQueue;
  private final DraftCommentsCleanupRunner cleanupRunner;

  @Inject
  CleanupDraftComments(WorkQueue workQueue, DraftCommentsCleanupRunner cleanupRunner) {
    this.workQueue = workQueue;
    this.cleanupRunner = cleanupRunner;
  }

  @Override
  public Response<?> apply(ConfigResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    var unused = workQueue.getDefaultQueue().submit(cleanupRunner);
    return Response.accepted("Cleanup of draft comments submitted");
  }
}
