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
import com.google.common.base.MoreObjects;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.Url;
import java.util.Optional;

@AutoValue
public abstract class ChangeDuplet {
  public static String format(Change change) {
    return format(change.getProject(), change.getKey());
  }

  private static String format(Project.NameKey project, Change.Key change) {
    return Url.encode(project.get()) + "~" + change.get();
  }

  /**
   * Parse a duplet out of a string.
   *
   * @param duplet string of the form "project~id".
   * @return the duplet if the input string has the proper format, or absent if not.
   */
  public static Optional<ChangeDuplet> parse(String duplet) {
    int z = duplet.lastIndexOf('~');
    return parse(duplet, z);
  }

  public static Optional<ChangeDuplet> parse(String triplet, int z) {
    if (z < 0) {
      return Optional.empty();
    }

    String project = Url.decode(triplet.substring(0, z));
    String changeId = triplet.substring(z + 1);
    return Optional.of(new AutoValue_ChangeDuplet(Project.nameKey(project), Change.key(changeId)));
  }

  public abstract Project.NameKey project();

  public abstract Change.Key id();

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this).add("project", project()).add("id", id()).toString();
  }
}
