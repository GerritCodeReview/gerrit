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

package com.google.gerrit.server;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.inject.Provider;

import javax.inject.Inject;

public class WebLinksProvider implements Provider<WebLinks> {

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<FileWebLink> fileLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;
  private final DynamicSet<BranchWebLink> branchLinks;

  @Inject
  public WebLinksProvider(DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<FileWebLink> fileLinks,
      DynamicSet<ProjectWebLink> projectLinks,
      DynamicSet<BranchWebLink> branchLinks) {
    this.patchSetLinks = patchSetLinks;
    this.fileLinks = fileLinks;
    this.projectLinks = projectLinks;
    this.branchLinks = branchLinks;
  }

  @Override
  public WebLinks get() {
    return new WebLinks(patchSetLinks, fileLinks, projectLinks, branchLinks);
  }
}
