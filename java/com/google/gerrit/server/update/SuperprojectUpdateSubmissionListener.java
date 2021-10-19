// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleOp;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Update superprojects after submission is done */
public class SuperprojectUpdateSubmissionListener implements SubmissionListener {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubmoduleOp.Factory subOpFactory;
  private final Map<BranchNameKey, ReceiveCommand> updatedBranches = new HashMap<>();
  private ImmutableList<BatchUpdate> batchUpdates = ImmutableList.of();
  private boolean dryrun;

  public static class SuperprojectUpdateSubmissionListenerModule extends AbstractModule {
    @Provides
    @SuperprojectUpdateOnSubmission
    ImmutableList<SubmissionListener> provideSubmissionListeners(
        SuperprojectUpdateSubmissionListener listener) {
      return ImmutableList.of(listener);
    }
  }

  @Inject
  public SuperprojectUpdateSubmissionListener(SubmoduleOp.Factory subOpFactory) {
    this.subOpFactory = subOpFactory;
  }

  @Override
  public void setDryrun() {
    this.dryrun = true;
  }

  @Override
  public void beforeBatchUpdates(Collection<BatchUpdate> updates) {
    if (!batchUpdates.isEmpty()) {
      // This is a retry. Save previous updates, as they are not in the new BatchUpdate.
      collectSuccessfullUpdates();
    }
    this.batchUpdates = ImmutableList.copyOf(updates);
  }

  @Override
  public void afterSubmission(MergeOpRepoManager orm) {
    collectSuccessfullUpdates();
    // Update superproject gitlinks if required.
    if (!updatedBranches.isEmpty()) {
      try {
        SubmoduleOp op = subOpFactory.create(updatedBranches, orm);
        op.updateSuperProjects(dryrun);
      } catch (RestApiException e) {
        logger.atWarning().withCause(e).log("Can't update the superprojects");
      }
    }
  }

  @Override
  public Optional<BatchUpdateListener> listensToBatchUpdates() {
    return Optional.empty();
  }

  private void collectSuccessfullUpdates() {
    if (!this.batchUpdates.isEmpty()) {
      for (BatchUpdate bu : batchUpdates) {
        updatedBranches.putAll(bu.getSuccessfullyUpdatedBranches(dryrun));
      }
    }
  }
}
