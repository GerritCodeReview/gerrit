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

import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gerrit.server.util.SimplifiedRequestScopePropagator;
import com.google.gerrit.server.util.WhoAmI;
import com.google.inject.Inject;
import com.google.inject.Provider;

class BackgroundAction implements UiAction<RevisionResource>,
    RestModifyView<RevisionResource, BackgroundAction.Input> {

  private final SimplifiedRequestScopePropagator rsp;
  private final Executor executor;
  private final WhoAmI job;
  private final Provider<CurrentUser> user;

  static class Input {
  }

  @Inject
  BackgroundAction(SimplifiedRequestScopePropagator rsp,
      WorkQueue queues,
      WhoAmI job,
      Provider<CurrentUser> user) {
    this.rsp = rsp;
    this.executor = queues.createQueue(1, "ReceiveCommits");
    this.job = job;
    this.user = user;
  }

  @Override
  public String apply(RevisionResource rev, Input input) {
    executor.submit(rsp.wrap(job));
    return null;
  }

  @Override
  public Description getDescription(RevisionResource resource) {
    return new Description().setLabel("Schedule")
        .setTitle("Schedule action in background")
        .setVisible(user.get().isIdentifiedUser());
  }
}
