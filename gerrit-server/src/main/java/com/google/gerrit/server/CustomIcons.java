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
import com.google.gerrit.extensions.common.CustomIconInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.ProjectCustomIcon;
import com.google.inject.Inject;

import java.util.List;

public class CustomIcons {

  private final DynamicSet<ProjectCustomIcon> projectIcons;

  @Inject
  public CustomIcons(DynamicSet<ProjectCustomIcon> projectIcons) {
    this.projectIcons = projectIcons;
  }

  public Iterable<Icon> getProjectIcons(String project) {
    List<Icon> icons = Lists.newArrayList();
    for (ProjectCustomIcon customIcon : projectIcons) {
      for(CustomIconInfo icon : customIcon.getIcons(project)) {
        icons.add(new Icon(icon.name, icon.path));
      }
    }
    return icons;
  }

  public class Icon {
    public String name;
    public String path;

    public Icon(String name, String path) {
      this.name = name;
      this.path = path;
    }
  }
}
