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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushCertificate;

/**
 * Performs an update on {@code All-Users} asynchronously if required. No-op in case no updates were
 * scheduled for asynchronous execution.
 */
public class AllUsersAsyncUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<PersonIdent> serverIdent;
  private final ExecutorService executor;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final ListMultimap<String, ChangeDraftUpdate> draftUpdates;
  private final ChangeDraftUpdate.Factory changeDraftUpdateFactory;

  @Inject
  AllUsersAsyncUpdate(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      @FanOutExecutor ExecutorService executor,
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      ChangeDraftUpdate.Factory changeDraftUpdateFactory) {
    this.serverIdent = serverIdent;
    this.executor = executor;
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.draftUpdates = MultimapBuilder.hashKeys().arrayListValues().build();
    this.changeDraftUpdateFactory = changeDraftUpdateFactory;
  }

  void setDraftUpdates(ListMultimap<String, ChangeDraftUpdate> draftUpdates) {
    checkState(isEmpty(), "attempted to set draft comment updates for async execution twice");
    boolean isNonPublishOnly =
        draftUpdates.values().stream().anyMatch(ChangeDraftUpdate::isPublishOnly);
    checkState(
        isNonPublishOnly,
        "attempted schedule draft comment operations for async execution that not publish-only");
    // Add deep copies to avoid any threading issues. Only share immutable objects.
    for (Map.Entry<String, Collection<ChangeDraftUpdate>> entry : draftUpdates.asMap().entrySet()) {
      for (ChangeDraftUpdate update : entry.getValue()) {
        ChangeDraftUpdate clonedUpdate =
            changeDraftUpdateFactory.create(
                new Change(update.getChange()), // Not a deep copy, but good enough
                update.accountId,
                update.realAccountId,
                update.authorIdent,
                update.when);
        clonedUpdate.deletePublishedComments(update.getCommentsToDelete());
        this.draftUpdates.put(entry.getKey(), clonedUpdate);
      }
    }
  }

  /** Returns true if no operations should be performed on the repo. */
  boolean isEmpty() {
    return draftUpdates.isEmpty();
  }

  /** Executes repository update asynchronously. No-op in case no updates were scheduled. */
  void execute(PersonIdent refLogIdent, String refLogMessage, PushCertificate pushCert) {
    if (isEmpty()) {
      return;
    }

    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        executor.submit(
            () -> {
              try (OpenRepo allUsersRepo = OpenRepo.open(repoManager, allUsersName)) {
                allUsersRepo.addUpdates(draftUpdates);
                allUsersRepo.flush();
                BatchRefUpdate bru = allUsersRepo.repo.getRefDatabase().newBatchUpdate();
                bru.setPushCertificate(pushCert);
                if (refLogMessage != null) {
                  bru.setRefLogMessage(refLogMessage, false);
                } else {
                  bru.setRefLogMessage(
                      firstNonNull(NoteDbUtil.guessRestApiHandler(), "Update NoteDb refs"), false);
                }
                bru.setRefLogIdent(refLogIdent != null ? refLogIdent : serverIdent.get());
                bru.setAtomic(true);
                allUsersRepo.cmds.addTo(bru);
                bru.setAllowNonFastForwards(true);
                RefUpdateUtil.executeChecked(bru, allUsersRepo.rw);
              } catch (IOException e) {
                logger.atSevere().withCause(e).log(
                    "Failed to delete draft comments asynchronously after publishing them");
              }
            });
  }
}
