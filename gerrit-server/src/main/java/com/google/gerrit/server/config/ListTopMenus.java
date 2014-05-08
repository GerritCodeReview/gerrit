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
import com.google.inject.Singleton;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.List;

@Singleton
class ListTopMenus implements RestReadView<ConfigResource> {
  private final DynamicSet<TopMenu> extensions;
  private final Provider<GerritTopMenus> topMenusProvider;

  private Project.NameKey currentProject;
  @Option(name = "-p", usage = "Current project name in focus for top menubar")
  public void setProjectFocus(String projectName) {
    currentProject = Project.NameKey.parse(projectName);
  }

  @Option(name = "--all", usage = "Gets all entries in Gerrit top menubar")
  private boolean returnAllMenusEnabled;

  @Inject
  ListTopMenus(DynamicSet<TopMenu> extensions, Provider<GerritTopMenus> gtmp) {
    this.extensions = extensions;
    this.topMenusProvider = gtmp;
  }

  @Override
  public List<TopMenu.MenuEntry> apply(ConfigResource resource) {
    List<TopMenu.MenuEntry> entries = Lists.newArrayList();

    if (returnAllMenusEnabled) {
      entries.addAll(topMenusProvider.get().getTopMenuBar(true, currentProject));
    }

    for (TopMenu extension : extensions) {
      entries.addAll(extension.getEntries());
    }
    return entries;
  }
}
