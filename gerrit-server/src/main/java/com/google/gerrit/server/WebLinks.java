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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.inject.Inject;

import java.util.List;

public class WebLinks {

  private DynamicSet<PatchSetWebLink> patchSetLinks;

  @Inject
  public WebLinks(final DynamicSet<PatchSetWebLink> patchSetLinks) {
    this.patchSetLinks = patchSetLinks;
  }

  public Iterable<Link> getPatchSetLinks(final String project,
      final String commit) {
    List<Link> links = Lists.newArrayList();
    for (PatchSetWebLink webLink : patchSetLinks) {
      links.add(new Link(webLink.getLinkName(),
          webLink.getPatchSetUrl(project, commit)));
    }
    return links;
  }

  public class Link {
    public String name;
    public String url;

    public Link(String name, String url) {
      this.name = name;
      this.url = url;
    }
  }
}
