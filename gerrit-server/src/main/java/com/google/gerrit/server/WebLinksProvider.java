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
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class WebLinksProvider implements Provider<WebLinks> {

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;

  @Inject
  public WebLinksProvider(DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<ProjectWebLink> projectLinks) {
    this.patchSetLinks = patchSetLinks;
    this.projectLinks = projectLinks;
  }

  @Override
  public WebLinks get() {
    WebLinks webLinks = new WebLinks(patchSetLinks, projectLinks);
    return webLinks;
  }
}
