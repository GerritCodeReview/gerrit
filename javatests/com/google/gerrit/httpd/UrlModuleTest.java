// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.common.truth.Truth.assertWithMessage;

import java.util.regex.Pattern;
import org.junit.Test;

public class UrlModuleTest {
  private static final Pattern TOOLS_PATTERN = Pattern.compile(UrlModule.TOOLS_REGEX);

  @Test
  public void toolsCatalogPathsMatch() {
    for (String path :
        new String[] {
          "/tools/hooks/commit-msg",
          "/a/tools/hooks/commit-msg",
          "/tools/hooks/commit-msg-legacy",
          "/tools/bin/gerrit-cherry-pick",
          "/a/tools/bin/gerrit-cherry-pick",
          "/tools/scripts/reposize.sh",
          "/tools/hooks",
          "/a/tools/hooks",
          "/tools/hooks/",
        }) {
      assertWithMessage("should match tools catalog: " + path)
          .that(TOOLS_PATTERN.matcher(path).matches())
          .isTrue();
    }
  }

  @Test
  public void gitAndLfsPathsForToolsProjectsDoNotMatch() {
    for (String path :
        new String[] {
          "/tools/example/info/refs",
          "/a/tools/example/info/refs",
          "/a/tools/example/git-upload-pack",
          "/a/tools/example/git-receive-pack",
          "/tools/arelease/info/lfs/objects/batch",
          "/a/tools/arelease/info/lfs/objects/batch",
          "/a/tools/my-repo/info/refs?service=git-upload-pack",
          "/tools/group/example/git-upload-pack",
        }) {
      assertWithMessage("should not match tools project: " + path)
          .that(TOOLS_PATTERN.matcher(path).matches())
          .isFalse();
    }
  }
}
