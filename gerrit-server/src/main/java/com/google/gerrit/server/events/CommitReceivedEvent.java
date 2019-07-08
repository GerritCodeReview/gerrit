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

public class CommitReceivedEvent extends RefEvent {
  static final String TYPE = "commit-received";
  public ReceiveCommand command;
  public Project project;
  public String refName;
  public RevCommit commit;
  public IdentifiedUser user;

  public CommitReceivedEvent() {
    super(TYPE);
  }

  public CommitReceivedEvent(
      ReceiveCommand command,
      Project project,
      String refName,
      RevCommit commit,
      IdentifiedUser user) {
    this();
    this.command = command;
    this.project = project;
    this.refName = refName;
    this.commit = commit;
    this.user = user;
  }

  @Override
  public Project.NameKey getProjectNameKey() {
    return project.getNameKey();
  }

  @Override
  public String getRefName() {
    return refName;
  }
}
