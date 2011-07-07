// Copyright (C) 2011 The Android Open Source Project
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
package com.google.gerrit.test.util;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;

public class RepositoryUtil {

  public static void deleteRepository(Git git) throws IOException {
    if (git != null) {
      git.getRepository().close();
      final File gitDir = git.getRepository().getDirectory();
      FileUtils.deleteDirectory(gitDir);
      if (FileUtils.sizeOfDirectory(gitDir.getParentFile()) == 0) {
        FileUtils.deleteDirectory(gitDir.getParentFile());
      }
    }
  }

}
