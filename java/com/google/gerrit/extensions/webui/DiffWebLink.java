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

package com.google.gerrit.extensions.webui;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;

@ExtensionPoint
public interface DiffWebLink extends WebLink {

  /**
   * {@link com.google.gerrit.extensions.common.DiffWebLinkInfo} describing a link from a file diff
   * to an external service.
   *
   * <p>In order for the web link to be visible {@link
   * com.google.gerrit.extensions.common.WebLinkInfo#url} and {@link
   * com.google.gerrit.extensions.common.WebLinkInfo#name} must be set.
   *
   * <p>
   *
   * @param projectName Name of the project
   * @param changeId ID of the change
   * @param patchSetIdA Patch set ID of side A, <code>null</code> if no base patch set was selected
   * @param revisionA Name of the revision of side A (e.g. branch or commit ID)
   * @param fileNameA Name of the file of side A
   * @param patchSetIdB Patch set ID of side B
   * @param revisionB Name of the revision of side B (e.g. branch or commit ID)
   * @param fileNameB Name of the file of side B
   * @return WebLinkInfo that links to file diff in external service, null if there should be no
   *     link.
   */
  DiffWebLinkInfo getDiffLink(
      String projectName,
      int changeId,
      Integer patchSetIdA,
      String revisionA,
      String fileNameA,
      int patchSetIdB,
      String revisionB,
      String fileNameB);
}
