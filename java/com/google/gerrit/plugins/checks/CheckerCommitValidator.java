// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks;

import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;

/** Rejects updates to checker branches. */
@Listen
@Singleton
public class CheckerCommitValidator implements CommitValidationListener {
  private final AllProjectsName allProjects;

  @Inject
  public CheckerCommitValidator(AllProjectsName allProjects) {
    this.allProjects = allProjects;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    if (!allProjects.equals(receiveEvent.project.getNameKey())
        || !CheckerRef.isRefsCheckers(receiveEvent.getRefName())) {
      return Collections.emptyList();
    }

    if (MagicBranch.isMagicBranch(receiveEvent.command.getRefName())
        || RefNames.isRefsChanges(receiveEvent.command.getRefName())) {
      throw new CommitValidationException("creating change for checker ref not allowed");
    }
    throw new CommitValidationException("direct update of checker ref not allowed");
  }
}
