// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.git.QueueProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.concurrent.ScheduledThreadPoolExecutor;

class CommandExecutorProvider implements Provider<ScheduledThreadPoolExecutor> {
  private final CapabilityControl.Factory capabilityFactory;
  private final QueueProvider queues;
  private final CurrentUser user;

  @Inject
  CommandExecutorProvider(
      CapabilityControl.Factory capabilityFactory, QueueProvider queues, CurrentUser user) {
    this.capabilityFactory = capabilityFactory;
    this.queues = queues;
    this.user = user;
  }

  @Override
  public ScheduledThreadPoolExecutor get() {
    return queues.getQueue(capabilityFactory.create(user).getQueueType());
  }
}
