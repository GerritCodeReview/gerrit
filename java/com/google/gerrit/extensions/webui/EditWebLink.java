// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.extensions.common.WebLinkInfo;

@ExtensionPoint
public interface EditWebLink extends WebLink {

  /**
   * {@link com.google.gerrit.extensions.common.WebLinkInfo} describing a link from a file to an
   * external service for editing.
   *
   * <p>In order for the web link to be visible {@link WebLinkInfo#url} and {@link WebLinkInfo#name}
   * must be set.
   *
   * @param projectName name of the project
   * @param revision name of the revision (e.g. branch or commit ID)
   * @param fileName name of the file
   * @return WebLinkInfo that links to project in external service, null if there should be no link.
   */
  WebLinkInfo getEditWebLink(String projectName, String revision, String fileName);
}
