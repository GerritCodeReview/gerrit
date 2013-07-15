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
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TransferConfig;
import com.google.inject.Inject;

import java.util.Map;

public class GetConfig implements RestReadView<ProjectResource> {

  private final TransferConfig config;

  @Inject
  public GetConfig(TransferConfig config) {
    this.config = config;
  }

  @Override
  public ConfigInfo apply(ProjectResource resource) {
    ConfigInfo result = new ConfigInfo();
    RefControl refConfig = resource.getControl()
        .controlForRef(GitRepositoryManager.REF_CONFIG);
    ProjectState state = resource.getControl().getProjectState();
    Project p = state.getProject();
    if (refConfig.isVisible()) {
      InheritedBooleanInfo useContributorAgreements = new InheritedBooleanInfo();
      InheritedBooleanInfo useSignedOffBy = new InheritedBooleanInfo();
      InheritedBooleanInfo useContentMerge = new InheritedBooleanInfo();
      InheritedBooleanInfo requireChangeId = new InheritedBooleanInfo();

      useContributorAgreements.value = state.isUseContributorAgreements();
      useSignedOffBy.value = state.isUseSignedOffBy();
      useContentMerge.value = state.isUseContentMerge();
      requireChangeId.value = state.isRequireChangeID();

      useContributorAgreements.configuredValue = p.getUseContributorAgreements();
      useSignedOffBy.configuredValue = p.getUseSignedOffBy();
      useContentMerge.configuredValue = p.getUseContentMerge();
      requireChangeId.configuredValue = p.getRequireChangeID();

      ProjectState parentState = Iterables.getFirst(state.parents(), null);
      if (parentState != null) {
        useContributorAgreements.inheritedValue = parentState.isUseContributorAgreements();
        useSignedOffBy.inheritedValue = parentState.isUseSignedOffBy();
        useContentMerge.inheritedValue = parentState.isUseContentMerge();
        requireChangeId.inheritedValue = parentState.isRequireChangeID();
      }

      result.useContributorAgreements = useContributorAgreements;
      result.useSignedOffBy = useSignedOffBy;
      result.useContentMerge = useContentMerge;
      result.requireChangeId = requireChangeId;
    }

    MaxObjectSizeLimitInfo maxObjectSizeLimit = new MaxObjectSizeLimitInfo();
    maxObjectSizeLimit.value =
        config.getEffectiveMaxObjectSizeLimit(state) == config.getMaxObjectSizeLimit()
            ? config.getFormattedMaxObjectSizeLimit()
            : p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.configuredValue = p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.inheritedValue = config.getFormattedMaxObjectSizeLimit();
    result.maxObjectSizeLimit = maxObjectSizeLimit;

    result.submitType = p.getSubmitType();
    result.state = p.getState() != Project.State.ACTIVE ? p.getState() : null;

    // commentlinks are visible to anyone, as they are used for linkification
    // on the client side.
    result.commentlinks = Maps.newLinkedHashMap();
    for (CommentLinkInfo cl : state.getCommentLinks()) {
      result.commentlinks.put(cl.name, cl);
    }

    // Themes are visible to anyone, as they are rendered client-side.
    result.theme = state.getTheme();
    return result;
  }

  public static class ConfigInfo {
    public final String kind = "gerritcodereview#project_config";

    public InheritedBooleanInfo useContributorAgreements;
    public InheritedBooleanInfo useContentMerge;
    public InheritedBooleanInfo useSignedOffBy;
    public InheritedBooleanInfo requireChangeId;
    public MaxObjectSizeLimitInfo maxObjectSizeLimit;
    public SubmitType submitType;
    public Project.State state;

    public Map<String, CommentLinkInfo> commentlinks;
    public ThemeInfo theme;
  }

  public static class InheritedBooleanInfo {
    public Boolean value;
    public InheritableBoolean configuredValue;
    public Boolean inheritedValue;
  }

  public static class MaxObjectSizeLimitInfo {
    public String value;
    public String configuredValue;
    public String inheritedValue;
  }
}
