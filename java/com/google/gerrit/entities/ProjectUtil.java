// Copyright (C) 2012 The Android Open Source Project
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

public class ProjectUtil {
  public static class InvalidProjectNameException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public InvalidProjectNameException(String message) {
      super(message);
    }
  }

  /**
   * Validates that a project name does not use an unsupported Git URL spelling.
   *
   * <p>Call this when accepting project names from user-controlled request paths, command-line
   * arguments, or request bodies. A single trailing {@code .git} suffix is valid. Repeated terminal
   * {@code .git} suffixes are rejected because they can otherwise create multiple logical project
   * names for the same physical repository.
   *
   * @param name project name to validate
   * @throws InvalidProjectNameException if the project name uses repeated terminal {@code .git}
   *     suffixes
   */
  public static void validateProjectName(String name) throws InvalidProjectNameException {
    name = stripTrailingSlash(name);
    if (name.endsWith(".git.git")) {
      throw new InvalidProjectNameException(
          String.format("Project cannot end in repeated .git suffixes: %s", name));
    }
  }

  /**
   * Normalizes a project name supplied using Git URL spelling.
   *
   * <p>Trailing slashes are removed first, then at most one trailing {@code .git} suffix is
   * removed. This preserves Gerrit's longstanding behavior for repository names supplied as Git
   * URLs, such as {@code project.git} or {@code project.git/}.
   *
   * <p>This method only normalizes. It does not reject unsupported project names. Call {@link
   * #validateProjectName(String)} before sanitizing user-controlled input when invalid spellings
   * must be rejected.
   *
   * @param name project name to normalize
   * @return normalized project name
   */
  public static String sanitizeProjectName(String name) {
    name = stripGitSuffix(name);
    name = stripTrailingSlash(name);
    return name;
  }

  public static String stripGitSuffix(String name) {
    if (name.endsWith(".git")) {
      // Be nice and drop the trailing ".git" suffix, which we never keep
      // in our database, but clients might mistakenly provide anyway.
      //
      name = name.substring(0, name.length() - 4);
      name = stripTrailingSlash(name);
    }
    return name;
  }

  private static String stripTrailingSlash(String name) {
    while (name.endsWith("/")) {
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }
}
