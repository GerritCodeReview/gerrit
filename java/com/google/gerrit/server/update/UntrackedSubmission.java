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

import java.util.Map;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Empty implementation of the {@link Submission} interface.
 *
 * <p>Do not announce any work and do nothing on completion. The {@link SubmissionContext} always
 * reports 0 changes announced.
 */
public class UntrackedSubmission implements Submission {

  @Override
  public BatchRefUpdate attachTo(BatchRefUpdate bru) {
    return bru;
  }

  @Override
  public void finish() {
    // noop
  }

  public static class Preparation implements Submission.Preparation {

    @Override
    public void prepareFor(Repository repository, Map<String, ReceiveCommand> refUpdates) {}

    @Override
    public Submission finish() {
      return new UntrackedSubmission();
    }
  }
}
