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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class NoOpSubmission implements Submission {

  @Override
  public SubmissionContext getSubmissionContext() {
    return null;
  }

  @Override
  public void completed() {
    // noop
  }

  public static class Builder implements Submission.Builder {

    @Override
    public void addEntry(Repository repository, Map<String, ReceiveCommand> refUpdates) {}

    @Override
    public Submission done() {
      return new NoOpSubmission();
    }
  }
}
