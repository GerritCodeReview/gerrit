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

import com.google.auto.value.AutoValue;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Optional;

@AutoValue
public abstract class ChangeTriplet {
  public static String format(Change change) {
    return format(change.getDest(), change.getKey());
  }

  private static String format(Branch.NameKey branch, Change.Key change) {
    return branch.getParentKey().get() + "~" + branch.getShortName() + "~" + change.get();
  }

  /**
   * Parse a triplet out of a string.
   *
   * @param triplet string of the form "project~branch~id".
   * @return the triplet if the input string has the proper format, or absent if not.
   */
  public static Optional<ChangeTriplet> parse(String triplet) {
    int t2 = triplet.lastIndexOf('~');
    int t1 = triplet.lastIndexOf('~', t2 - 1);
    if (t1 < 0 || t2 < 0) {
      return Optional.empty();
    }

    String project = Url.decode(triplet.substring(0, t1));
    String branch = Url.decode(triplet.substring(t1 + 1, t2));
    String changeId = Url.decode(triplet.substring(t2 + 1));

    ChangeTriplet result =
        new AutoValue_ChangeTriplet(
            new Branch.NameKey(new Project.NameKey(project), branch), new Change.Key(changeId));
    return Optional.of(result);
  }

  public final Project.NameKey project() {
    return branch().getParentKey();
  }

  public abstract Branch.NameKey branch();

  public abstract Change.Key id();

  @Override
  public String toString() {
    return format(branch(), id());
  }
}
