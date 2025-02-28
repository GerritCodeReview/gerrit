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

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.Url;
import java.util.Optional;

public record ChangeTriplet(BranchNameKey branch, Change.Key id) {
  public ChangeTriplet {
    requireNonNull(branch, "branch");
    requireNonNull(id, "id");
  }

  public static String format(Change change) {
    return format(change.getDest(), change.getKey());
  }

  private static String format(BranchNameKey branch, Change.Key change) {
    return Url.encode(branch.project().get())
        + "~"
        + Url.encode(branch.shortName())
        + "~"
        + change.get();
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
    String changeId = triplet.substring(z + 1);
    return Optional.of(
        new ChangeTriplet(
            BranchNameKey.create(Project.nameKey(project), branch), Change.key(changeId)));
  }

  public final Project.NameKey project() {
    return branch().project();
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("project", branch().project())
        .add("branch", branch().shortName())
        .add("id", id())
        .toString();
  }
}
