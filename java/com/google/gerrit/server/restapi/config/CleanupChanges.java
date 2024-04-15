// Copyright (C) 2024 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeCleanupRunner;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.restapi.config.CleanupChanges.Input;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RequiresCapability(GlobalCapability.MAINTAIN_SERVER)
@Singleton
public class CleanupChanges implements RestModifyView<ConfigResource, Input> {
  private final ChangeCleanupRunner.Factory runnerFactory;
  private final WorkQueue workQueue;

  public static class Input {
    String after;
    boolean ifMergeable;
    String message;
  }

  @Inject
  CleanupChanges(WorkQueue workQueue, ChangeCleanupRunner.Factory runnerFactory) {
    this.runnerFactory = runnerFactory;
    this.workQueue = workQueue;
  }

  @Override
  public Response<?> apply(ConfigResource rsrc, Input input) throws BadRequestException {
    if (taskAlreadyScheduled()) {
      return Response.ok("Change cleaner already in queue.");
    }
    if (input.after == null) {
      throw new BadRequestException("`after` must be specified.");
    }
    ChangeCleanupRunner runner =
        runnerFactory.create(
            ConfigUtil.getTimeUnit(input.after, 0, TimeUnit.MILLISECONDS),
            input.ifMergeable,
            input.message);
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = workQueue.getDefaultQueue().submit(() -> runner.run());
    return Response.accepted("Change cleaner task added to work queue.");
  }

  private boolean taskAlreadyScheduled() {
    for (WorkQueue.Task<?> task : workQueue.getTasks()) {
      if (task.toString().contains(ChangeCleanupRunner.class.getName())) {
        return true;
      }
    }
    return false;
  }
}
