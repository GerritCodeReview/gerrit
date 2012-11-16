// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

class GetProject implements RestReadView<ProjectResource> {
  @Override
  public Object apply(ProjectResource resource) throws UnsupportedEncodingException {
    Project project = resource.getControl().getProject();
    ProjectInfo info = new ProjectInfo();
    info.name = resource.getName();
    info.parent = Strings.emptyToNull(project.getParentName());
    info.description = Strings.emptyToNull(project.getDescription());
    info.finish();
    return info;
  }

  static class ProjectInfo {
    final String kind = "gerritcodereview#project";
    String id;
    String name;
    String parent;
    String description;

    void finish() throws UnsupportedEncodingException {
      id = URLEncoder.encode(name, "UTF-8");
    }
  }
}
