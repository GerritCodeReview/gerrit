// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

import java.util.Objects;

public class ProjectWatchInfo {
  public String project;
  public String filter;

  public Boolean notifyNewChanges;
  public Boolean notifyNewPatchSets;
  public Boolean notifyAllComments;
  public Boolean notifySubmittedChanges;
  public Boolean notifyAbandonedChanges;

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ProjectWatchInfo) {
      ProjectWatchInfo w = (ProjectWatchInfo) obj;
      return Objects.equals(project, w.project)
          && Objects.equals(filter, w.filter)
          && Objects.equals(notifyNewChanges, w.notifyNewChanges)
          && Objects.equals(notifyNewPatchSets, w.notifyNewPatchSets)
          && Objects.equals(notifyAllComments, w.notifyAllComments)
          && Objects.equals(notifySubmittedChanges, w.notifySubmittedChanges)
          && Objects.equals(notifyAbandonedChanges, w.notifyAbandonedChanges);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        project,
        filter,
        notifyNewChanges,
        notifyNewPatchSets,
        notifyAllComments,
        notifySubmittedChanges,
        notifyAbandonedChanges);
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(project);
    if (filter != null) {
      b.append("%filter=").append(filter);
    }
    b.append("(notifyAbandonedChanges=")
        .append(toBoolean(notifyAbandonedChanges))
        .append(", notifyAllComments=")
        .append(toBoolean(notifyAllComments))
        .append(", notifyNewChanges=")
        .append(toBoolean(notifyNewChanges))
        .append(", notifyNewPatchSets=")
        .append(toBoolean(notifyNewPatchSets))
        .append(", notifySubmittedChanges=")
        .append(toBoolean(notifySubmittedChanges))
        .append(")");
    return b.toString();
  }

  private boolean toBoolean(Boolean b) {
    return b == null ? false : b;
  }
}
