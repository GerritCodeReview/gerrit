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

/**
 * Description of a submission passed to BatchRefUpdate so they can report their result.
 *
 * <p>Implementors can subclass this context to add their storage-specific details.
 */
public class SubmissionContext {
  private final String submissionId;
  private final int updatesCount;

  SubmissionContext(String submissionId, int updatesCount) {
    this.updatesCount = updatesCount;
    this.submissionId = submissionId;
  }

  public int getUpdatesCount() {
    return updatesCount;
  }

  public String getSubmissionId() {
    return submissionId;
  }
}
