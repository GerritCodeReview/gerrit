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

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.OptionUtil;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.kohsuke.args4j.Option;

import java.util.Map;
import java.util.Set;

class GetConfig implements RestReadView<ProjectResource> {
  @Option(name = "-q", metaVar = "OPT", multiValued = true,
      usage = "Config option to inspect")
  void addQuery(String name) {
    if (query == null) {
      query = Sets.newHashSet();
    }
    Iterables.addAll(query, OptionUtil.splitOptionValue(name));
  }
  private Set<String> query;

  static class ConfigInfo {
    Boolean useContributorAgreements;
    Boolean useContentMerge;
    Boolean useSignedOffBy;
    Boolean requireChangeID;

    Map<String, CommentLinkInfo> commentlinks;
  }

  @Override
  public Object apply(ProjectResource resource) {
    ConfigInfo result = new ConfigInfo();
    RefControl refConfig = resource.getControl()
        .controlForRef(GitRepositoryManager.REF_CONFIG);
    ProjectState project = resource.getControl().getProjectState();
    if (refConfig.isVisible()) {
      if (want("usecontributoragreements")) {
        result.useContributorAgreements = project.isUseContributorAgreements();
      }
      if (want("usecontentmerge")) {
        result.useContentMerge = project.isUseContentMerge();
      }
      if (want("usesignedoffby")) {
        result.useSignedOffBy = project.isUseSignedOffBy();
      }
      if (want("requirechangeid")) {
        result.requireChangeID = project.isRequireChangeID();
      }
    }
    if (want("commentlinks")) {
      // commentlinks are visible to anyone, as they are used for linkification
      // on the client side.
      result.commentlinks = Maps.newLinkedHashMap();
      for (CommentLinkInfo cl : project.getCommentLinks()) {
        result.commentlinks.put(cl.name, cl);
      }
    }
    return result;
  }

  private boolean want(String name) {
    return query == null || query.contains(name);
  }
}
