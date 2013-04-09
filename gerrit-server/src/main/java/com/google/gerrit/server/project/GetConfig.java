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

package com.google.gerrit.server.project;

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;

import java.util.Map;

public class GetConfig implements RestReadView<ProjectResource> {
  public static class ConfigInfo {
    public final String kind = "gerritcodereview#project_config";

    public Boolean useContributorAgreements;
    public Boolean useContentMerge;
    public Boolean useSignedOffBy;
    public Boolean requireChangeId;

    public Map<String, CommentLinkInfo> commentlinks;

    public ThemeInfo theme;
  }

  @Override
  public ConfigInfo apply(ProjectResource resource) {
    ConfigInfo result = new ConfigInfo();
    RefControl refConfig = resource.getControl()
        .controlForRef(GitRepositoryManager.REF_CONFIG);
    ProjectState project = resource.getControl().getProjectState();
    if (refConfig.isVisible()) {
      result.useContributorAgreements = project.isUseContributorAgreements();
      result.useContentMerge = project.isUseContentMerge();
      result.useSignedOffBy = project.isUseSignedOffBy();
      result.requireChangeId = project.isRequireChangeID();
    }

    // commentlinks are visible to anyone, as they are used for linkification
    // on the client side.
    result.commentlinks = Maps.newLinkedHashMap();
    for (CommentLinkInfo cl : project.getCommentLinks()) {
      result.commentlinks.put(cl.name, cl);
    }

    // Themes are visible to anyone, as they are rendered client-side.
    result.theme = project.getTheme();
    return result;
  }
}
