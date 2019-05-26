// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import java.util.Queue;
import org.eclipse.jgit.lib.ObjectId;

class TestHelperOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Change.Id changeId;
  private final TestSubmitInput input;

  TestHelperOp(Change.Id changeId, SubmitStrategy.Arguments args) {
    this.changeId = changeId;
    this.input = (TestSubmitInput) args.submitInput;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    Queue<Boolean> q = input.generateLockFailures;
    if (q != null && !q.isEmpty() && q.remove()) {
      logger.atFine().log(
          "Adding bogus ref update to trigger lock failure, via change %s", changeId);
      ctx.addRefUpdate(
          ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
          ObjectId.zeroId(),
          "refs/test/" + getClass().getSimpleName());
    }
  }
}
