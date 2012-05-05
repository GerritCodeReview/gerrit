// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

public class CommitReceivedEvent extends ChangeEvent {
  public final ReceiveCommand command;
  public final Project project;
  public final String refName;
  public final RevCommit commit;
  public final IdentifiedUser user;

  public CommitReceivedEvent(ReceiveCommand command, Project project,
      String refName, RevCommit commit, IdentifiedUser user) {
    this.command = command;
    this.project = project;
    this.refName = refName;
    this.commit = commit;
    this.user = user;
  }
}
