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
    return branch.project().get() + "~" + branch.shortName() + "~" + change.get();
  }

  /**
   * Parse a triplet out of a string.
   *
   * @param triplet string of the form "project~branch~id".
   * @return the triplet if the input string has the proper format, or absent if not.
   */
  public static Optional<ChangeTriplet> parse(String triplet) {
    int z = triplet.lastIndexOf('~');
    int y = triplet.lastIndexOf('~', z - 1);
    return parse(triplet, y, z);
  }

  public static Optional<ChangeTriplet> parse(String triplet, int y, int z) {
    if (y < 0 || z < 0) {
      return Optional.empty();
    }

    String project = Url.decode(triplet.substring(0, y));
    String branch = Url.decode(triplet.substring(y + 1, z));
    String changeId = Url.decode(triplet.substring(z + 1));
    return Optional.of(
        new AutoValue_ChangeTriplet(
            Branch.nameKey(Project.nameKey(project), branch), new Change.Key(changeId)));
  }

  public final Project.NameKey project() {
    return branch().project();
  }

  public abstract Branch.NameKey branch();

  public abstract Change.Key id();

  @Override
  public String toString() {
    return format(branch(), id());
  }
}
