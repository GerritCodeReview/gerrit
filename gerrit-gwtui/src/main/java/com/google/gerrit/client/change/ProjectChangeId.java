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

package com.google.gerrit.client.change;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Objects;

/** Provides logic for parsing a numeric change id and project from a URL. */
public class ProjectChangeId {

  /** Parses a {@link ProjectChangeId} from it's string representation. */
  public static ProjectChangeId create(String token) {
    String mutableToken = token;
    // Try parsing /c/project/+/numericChangeId where token is project/+/numericChangeId
    int delimiter = mutableToken.indexOf(PageLinks.PROJECT_CHANGE_DELIMITER);
    Project.NameKey project = null;
    if (delimiter > 0) {
      project = new Project.NameKey(token.substring(0, delimiter));
      mutableToken =
          mutableToken.substring(delimiter + PageLinks.PROJECT_CHANGE_DELIMITER.length());
    }

    // Try parsing /c/numericChangeId where token is numericChangeId
    int s = mutableToken.indexOf('/');
    if (s > 0) {
      mutableToken = mutableToken.substring(0, s);
    }
    // Special case: project/+/1233,edit/
    s = mutableToken.indexOf(",edit");
    if (s > 0) {
      mutableToken = mutableToken.substring(0, s);
    }
    Integer cId = tryParse(mutableToken);
    if (cId != null) {
      return new ProjectChangeId(project, new Change.Id(cId));
    }

    throw new IllegalArgumentException(token + " is not a valid change identifier");
  }

  @Nullable private final Project.NameKey project;
  private final Change.Id changeId;

  @VisibleForTesting
  ProjectChangeId(@Nullable Project.NameKey project, Change.Id changeId) {
    this.project = project;
    this.changeId = changeId;
  }

  @Nullable
  public Project.NameKey getProject() {
    return project;
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  /**
   * Calculate the length of the string representation of the change ID that was parsed from the
   * token.
   *
   * @return the length of the {@link com.google.gerrit.reviewdb.client.Change.Id} if no project was
   *     parsed from the token. The length of {@link
   *     com.google.gerrit.reviewdb.client.Project.NameKey} + the delimiter + the length of {@link
   *     com.google.gerrit.reviewdb.client.Change.Id} otherwise.
   */
  public int identifierLength() {
    if (project == null) {
      return String.valueOf(changeId).length();
    }
    return PageLinks.toChangeId(project, changeId).length();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ProjectChangeId) {
      ProjectChangeId other = (ProjectChangeId) obj;
      return Objects.equals(changeId, other.changeId) && Objects.equals(project, other.project);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeId, project);
  }

  @Override
  public String toString() {
    return "ProjectChangeId.Result{changeId: " + changeId + ", project: " + project + "}";
  }

  private static Integer tryParse(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
