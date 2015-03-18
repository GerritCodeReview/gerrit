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

package com.google.gerrit.server.git;

import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

@Singleton
public class ReloadSubmitQueueOp extends DefaultQueueOp {
  private static final Logger log =
      LoggerFactory.getLogger(ReloadSubmitQueueOp.class);

  private final OneOffRequestContext requestContext;
  private final Provider<InternalChangeQuery> queryProvider;
  private final MergeQueue mergeQueue;

  @Inject
  ReloadSubmitQueueOp(
      OneOffRequestContext rc,
      WorkQueue wq,
      Provider<InternalChangeQuery> qp,
      MergeQueue mq) {
    super(wq);
    requestContext = rc;
    queryProvider = qp;
    mergeQueue = mq;
  }

  @Override
  public void run() {
    try (AutoCloseable ctx = requestContext.open()) {
      for (ChangeData cd : queryProvider.get().allSubmitted()) {
        try {
          // TODO(sbeller): Guess the correct lists instead of having each
          // change being in its own list. As of writing this todo, it's
          // only dependent on `submitwholetopic`
          mergeQueue.schedule(Arrays.asList(cd.change()));
        } catch (OrmException e) {
          log.error("Error reading submitted change", e);
        }
      }
    } catch (Exception e) {
      log.error("Cannot reload MergeQueue", e);
    }
  }

  @Override
  public String toString() {
    return "Reload Submit Queue";
  }
}
