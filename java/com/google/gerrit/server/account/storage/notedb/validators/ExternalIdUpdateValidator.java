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

package com.google.gerrit.server.account.storage.notedb.validators;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.externalids.ExternalIdsConsistencyChecker;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Validates updates to refs/meta/external-ids. */
public class ExternalIdUpdateValidator implements CommitValidationListener {
  private final AllUsersName allUsers;
  private final AccountCache accountCache;
  private final ExternalIdsConsistencyChecker externalIdsConsistencyChecker;

  @Inject
  ExternalIdUpdateValidator(
      AllUsersName allUsers,
      ExternalIdsConsistencyChecker externalIdsConsistencyChecker,
      AccountCache accountCache) {
    this.externalIdsConsistencyChecker = externalIdsConsistencyChecker;
    this.allUsers = allUsers;
    this.accountCache = accountCache;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    if (allUsers.equals(receiveEvent.project.getNameKey())
        && RefNames.REFS_EXTERNAL_IDS.equals(receiveEvent.refName)) {
      try {
        List<ConsistencyProblemInfo> problems =
            externalIdsConsistencyChecker.check(accountCache, receiveEvent.commit);
        List<CommitValidationMessage> msgs =
            problems.stream()
                .map(
                    p ->
                        new CommitValidationMessage(
                            p.message,
                            p.status == ConsistencyProblemInfo.Status.ERROR
                                ? ValidationMessage.Type.ERROR
                                : ValidationMessage.Type.OTHER))
                .collect(toList());
        if (msgs.stream().anyMatch(ValidationMessage::isError)) {
          throw new CommitValidationException("invalid external IDs", msgs);
        }
        return msgs;
      } catch (IOException | ConfigInvalidException e) {
        throw new CommitValidationException("error validating external IDs", e);
      }
    }
    return Collections.emptyList();
  }
}
