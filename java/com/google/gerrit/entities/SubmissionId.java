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

package com.google.gerrit.entities;

import org.eclipse.jgit.annotations.Nullable;

public class SubmissionId {
  private final String submissionId;

  public SubmissionId(Change.Id changeId, @Nullable String topic) {
    submissionId = topic != null ? String.format("%s-%s", changeId, topic) : changeId.toString();
  }

  public SubmissionId(Change change) {
    this(change.getId(), change.getTopic());
  }

  @Override
  public String toString() {
    return submissionId;
  }
}
