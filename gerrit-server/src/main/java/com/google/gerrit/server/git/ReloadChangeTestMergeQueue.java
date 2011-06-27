// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReloadChangeTestMergeQueue extends DefaultQueueOp {
  public interface Factory {
    ReloadChangeTestMergeQueue create();
  }

  private static final Logger log =
      LoggerFactory.getLogger(ReloadSubmitQueueOp.class);

  private final SchemaFactory<ReviewDb> schema;
  private final ChangeTestMergeQueue changeTestMergeQueue;

  @Inject
  ReloadChangeTestMergeQueue(final WorkQueue wq, final SchemaFactory<ReviewDb> sf,
      final ChangeTestMergeQueue ctmq) {
    super(wq);
    schema = sf;
    changeTestMergeQueue = ctmq;
  }

  @Override
  public void run() {
    try {
      final ReviewDb c = schema.open();
      try {
        for (final Change change : c.changes().allNewMergeNotTested()) {
          changeTestMergeQueue.add(change);
        }
      } finally {
        c.close();
      }
    } catch (OrmException e) {
      log.error("Cannot reload ChangeTestMergeQueue", e);
    }
  }

  @Override
  public String toString() {
    return "Reload Change Test Merge Queue";
  }

}
