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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

public class ReloadSubmitQueueOp extends DefaultQueueOp {
  public interface Factory {
    ReloadSubmitQueueOp create();
  }

  private static final Logger log =
      LoggerFactory.getLogger(ReloadSubmitQueueOp.class);

  private final SchemaFactory<ReviewDb> schema;
  private final MergeQueue mergeQueue;

  @Inject
  ReloadSubmitQueueOp(final WorkQueue wq, final SchemaFactory<ReviewDb> sf,
      final MergeQueue mq) {
    super(wq);
    schema = sf;
    mergeQueue = mq;
  }

  public void run() {
    final HashSet<Branch.NameKey> pending = new HashSet<Branch.NameKey>();
    try {
      final ReviewDb c = schema.open();
      try {
        for (final Change change : c.changes().allSubmitted()) {
          pending.add(change.getDest());
        }
      } finally {
        c.close();
      }
    } catch (OrmException e) {
      log.error("Cannot reload MergeQueue", e);
    }

    for (final Branch.NameKey branch : pending) {
      mergeQueue.schedule(branch);
    }
  }

  @Override
  public String toString() {
    return "Reload Submit Queue";
  }
}
