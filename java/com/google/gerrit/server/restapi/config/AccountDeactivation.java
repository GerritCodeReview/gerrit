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
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountDeactivator;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Future;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class AccountDeactivation implements RestModifyView<ConfigResource, Input> {
  private final AccountDeactivator deactivator;
  private final WorkQueue workQueue;

  @Inject
  AccountDeactivation(WorkQueue workQueue, AccountDeactivator deactivator) {
    this.deactivator = deactivator;
    this.workQueue = workQueue;
  }

  @Override
  public Response<?> apply(ConfigResource rsrc, Input unusedInput) {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError = workQueue.getDefaultQueue().submit(() -> deactivator.run());
    return Response.ok("Account deactivator task added to work queue.");
  }
}
