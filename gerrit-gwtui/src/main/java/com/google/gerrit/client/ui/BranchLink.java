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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;

/** Link to the open changes of a project. */
public class BranchLink extends InlineHyperlink {
  private final String query;

  public BranchLink(Project.NameKey project, Change.Status status, String branch, String topic) {
    this(text(branch, topic), query(project, status, branch, topic));
  }

  public BranchLink(
      String text, Project.NameKey project, Change.Status status, String branch, String topic) {
    this(text, query(project, status, branch, topic));
  }

  private BranchLink(String text, String query) {
    super(text, PageLinks.toChangeQuery(query));
    this.query = query;
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }

  private Screen createScreen() {
    return QueryScreen.forQuery(query);
  }

  private static String text(String branch, String topic) {
    if (topic != null && !topic.isEmpty()) {
      return branch + " (" + topic + ")";
    }
    return branch;
  }

  public static String query(
      Project.NameKey project, Change.Status status, String branch, String topic) {
    String query = PageLinks.projectQuery(project, status);

    if (branch.startsWith(RefNames.REFS)) {
      if (branch.startsWith(RefNames.REFS_HEADS)) {
        query +=
            " "
                + PageLinks.op(
                    "branch", //
                    branch.substring(RefNames.REFS_HEADS.length()));
      } else {
        query += " " + PageLinks.op("ref", branch);
      }
    } else {
      // Assume it was clipped already by the caller.  This
      // happens for example inside of the ChangeInfo object.
      //
      query += " " + PageLinks.op("branch", branch);
    }

    if (topic != null && !topic.isEmpty()) {
      query += " " + PageLinks.op("topic", topic);
    }

    return query;
  }
}
