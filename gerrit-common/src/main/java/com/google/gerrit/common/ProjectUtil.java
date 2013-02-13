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

package com.google.gerrit.common;

import org.eclipse.jgit.lib.Constants;

public class ProjectUtil {
  public static String stripGitSuffix(String name) {
    String nameWithoutSuffix = name;

    if (nameWithoutSuffix.endsWith(Constants.DOT_GIT_EXT)) {
      // Be nice and drop the trailing ".git" suffix, which we never keep
      // in our database, but clients might mistakenly provide anyway.
      //
      nameWithoutSuffix = nameWithoutSuffix.substring(0, //
          nameWithoutSuffix.length() - Constants.DOT_GIT_EXT.length());
      while (nameWithoutSuffix.endsWith("/")) {
        nameWithoutSuffix =
            nameWithoutSuffix.substring(0, nameWithoutSuffix.length() - 1);
      }
    }
    return nameWithoutSuffix;
  }
}
