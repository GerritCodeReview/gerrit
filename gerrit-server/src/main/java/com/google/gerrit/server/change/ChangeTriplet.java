// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.lib.Constants;

public class ChangeTriplet {

  private final Change.Key changeKey;
  private final Project.NameKey projectNameKey;
  private final Branch.NameKey branchNameKey;

  public ChangeTriplet(final String triplet) throws ParseException {
    int t2 = triplet.lastIndexOf('~');
    int t1 = triplet.lastIndexOf('~', t2 - 1);
    if (t1 < 0 || t2 < 0) {
      throw new ParseException();
    }

    String project = Url.decode(triplet.substring(0, t1));
    String branch = Url.decode(triplet.substring(t1 + 1, t2));
    String changeId = Url.decode(triplet.substring(t2 + 1));

    if (!branch.startsWith(Constants.R_REFS)) {
      branch = Constants.R_HEADS + branch;
    }

    changeKey = new Change.Key(changeId);
    projectNameKey = new Project.NameKey(project);
    branchNameKey = new Branch.NameKey(projectNameKey, branch);
  }

  public Change.Key getChangeKey() {
    return changeKey;
  }

  public Branch.NameKey getBranchNameKey() {
    return branchNameKey;
  }

  public static String format(final Change change) {
    return change.getProject().get() + "~"
        + change.getDest().getShortName() + "~"
        + change.getKey().get();
  }

  public static class ParseException extends Exception {
    ParseException() {
      super();
    }
  }
}
