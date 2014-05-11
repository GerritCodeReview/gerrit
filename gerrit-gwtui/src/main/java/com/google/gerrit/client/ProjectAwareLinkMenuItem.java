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

package com.google.gerrit.client;

import com.google.gerrit.client.ui.ProjectLinkMenuItem;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.http.client.URL;
import com.google.gwt.http.client.UrlBuilder;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;

final class ProjectAwareLinkMenuItem extends ProjectLinkMenuItem {
  ProjectAwareLinkMenuItem(String text, String panel) {
    super(text, panel);
  }

  @Override
  protected void onScreenLoad(Project.NameKey project) {
  String p =
      panel.replace(Gerrit.PROJECT_NAME_MENU_VAR,
          URL.encodeQueryString(project.get()));
    if (!panel.startsWith("/x/") && !Gerrit.isAbsolute(panel)) {
      UrlBuilder builder = new UrlBuilder();
      builder.setProtocol(Location.getProtocol());
      builder.setHost(Location.getHost());
      String port = Location.getPort();
      if (port != null && !port.isEmpty()) {
        builder.setPort(Integer.parseInt(port));
      }
      builder.setPath(Location.getPath());
      p = builder.buildString() + p;
    }
    getElement().setPropertyString("href", p);
  }

  @Override
  public void go() {
    String href = getElement().getPropertyString("href");
    if (href.startsWith("#")) {
      super.go();
    } else {
      Window.open(href, getElement().getPropertyString("target"), "");
    }
  }
}