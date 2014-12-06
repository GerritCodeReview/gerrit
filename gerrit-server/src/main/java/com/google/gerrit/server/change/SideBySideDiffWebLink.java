// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.webui.DiffWebLink;
import com.google.gerrit.extensions.webui.WebLink.Target;

public class SideBySideDiffWebLink implements DiffWebLink {

  @Override
  public DiffWebLinkInfo getDiffLink(String projectName, int changeId,
      Integer patchSetIdA, String revisionA, String fileNameA,
      int patchSetIdB, String revisionB, String fileNameB) {
    return DiffWebLinkInfo.forUnifiedDiffView("side-by-side diff",
        "/static/sideBySideDiff.png",
        getUrl(changeId, patchSetIdA, patchSetIdB, fileNameB),
        Target.SELF);
  }

  public static String getUrl(int changeId, Integer patchSetIdA,
      int patchSetIdB, String fileName) {
    StringBuilder url = new StringBuilder();
    url.append("/c/");
    url.append(changeId);
    url.append("/");
    if (patchSetIdA != null) {
      url.append(patchSetIdA);
      url.append("..");
    }
    url.append(patchSetIdB);
    url.append("/");
    url.append(Url.encode(fileName));
    return url.toString();
  }
}
