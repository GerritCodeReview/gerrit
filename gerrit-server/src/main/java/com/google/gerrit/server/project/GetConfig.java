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
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.OptionUtil;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.kohsuke.args4j.Option;

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
  }

  @Override
  public Object apply(ProjectResource resource) {
    ConfigInfo result = new ConfigInfo();
    RefControl refConfig = resource.getControl()
        .controlForRef(GitRepositoryManager.REF_CONFIG);
    if (refConfig.isVisible()) {
      if (want("use_contributor_agreements")) {
        result.useContributorAgreements = project.isUseContributorAgreements();
      }
      if (want("use_content_merge")) {
        result.useContentMerge = project.isUseContentMerge();
      }
      if (want("use_signed_off_by")) {
        result.useSignedOffBy = project.isUseSignedOffBy();
      }
      if (want("require_change_id")) {
        result.requireChangeID = project.isRequireChangeID();
      }
    }
    return result;
  }

  private boolean want(String name) {
    return query == null || query.contains(name);
  }
}
