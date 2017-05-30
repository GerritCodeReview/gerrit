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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Objects;

/** Provides logic for parsing a numeric change id and project from a URL. */
public class ChangeIdParser {
  public static class Result {
    @Nullable public final Project.NameKey project;
    public final Change.Id changeId;

    public Result(@Nullable Project.NameKey project, Change.Id changeId) {
      this.project = project;
      this.changeId = changeId;
    }

    /**
     * Calculate the length of the string representation of the change ID that was parsed from the
     * token.
     *
     * @return the length of the {@code Change.Id} if no project was parsed from the token. The
     *     length of {@code Project.NameKey} + the delimiter + the length of {@code Change.Id}
     *     otherwise.
     */
    public int identifierLength() {
      if (project == null) {
        return String.valueOf(changeId).length();
      }
      return (project.get() + PageLinks.PROJECT_CHANGE_DELIMITER + String.valueOf(changeId))
          .length();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Result) {
        Result other = (Result) obj;
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
      return "ChangeIdParser.Result{changeId: " + changeId + ", project: " + project + "}";
    }
  }

  public static Result parse(String token) {
    // Try parsing /c/project/+/numericChangeId where token is project/+/numericChangeId
    int delimiter = token.indexOf(PageLinks.PROJECT_CHANGE_DELIMITER);
    if (delimiter > 0) {
      String project = token.substring(0, delimiter);
      String rest = token.substring(delimiter + PageLinks.PROJECT_CHANGE_DELIMITER.length());
      int s = rest.indexOf('/');
      if (s > 0) {
        rest = rest.substring(0, s);
      }
      // Special case: project~1233,edit/
      s = rest.indexOf(",edit");
      if (s > 0) {
        rest = rest.substring(0, s);
      }
      Integer n = tryParse(rest);
      if (n != null) {
        return new Result(new Project.NameKey(project), new Change.Id(n));
      }
    }

    // Try parsing /c/numericChangeId/path where token is numericChangeId/path
    String maybeChangeId = token;
    int idx = token.indexOf('/');
    if (idx > 0) {
      maybeChangeId = token.substring(0, idx);
    }
    // Special case: 1233,edit/
    idx = maybeChangeId.indexOf(",edit");
    if (idx > 0) {
      maybeChangeId = maybeChangeId.substring(0, idx);
    }
    Integer cId = tryParse(maybeChangeId);
    if (cId != null) {
      return new Result(null, new Change.Id(cId));
    }

    throw new IllegalArgumentException(token + " is not a valid change identifier");
  }

  private static Integer tryParse(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
