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

package com.google.gerrit.server.config;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.List;

class ListTopMenus implements RestReadView<ConfigResource> {
  private final DynamicSet<TopMenu> extensions;
  private final GerritTopMenus gerritTopMenus;

  private Project.NameKey currentProject;
  @Option(name = "-p", usage = "Current project name in focus for top menubar")
  public void setProjectFocus(String projectName) {
    currentProject = Project.NameKey.parse(projectName);
  }

  @Option(name = "--all", usage = "Gets all entries in Gerrit top menubar")
  private boolean returnAllMenusEnabled;

  @Inject
  ListTopMenus(DynamicSet<TopMenu> extensions, GerritTopMenus gerritTopMenus) {
    this.extensions = extensions;
    this.gerritTopMenus = gerritTopMenus;
  }

  @Override
  public List<TopMenu.MenuEntry> apply(ConfigResource resource) {
    List<TopMenu.MenuEntry> entries = Lists.newArrayList();

    if (returnAllMenusEnabled) {
      entries.addAll(gerritTopMenus.getTopMenuBar(true, currentProject));
    }

    for (TopMenu extension : extensions) {
      entries.addAll(extension.getEntries());
    }
    return entries;
  }
}
