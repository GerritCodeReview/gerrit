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

import java.util.Map;

public class GetConfig implements RestReadView<ProjectResource> {

  @Override
  public ConfigInfo apply(ProjectResource resource) {
    ConfigInfo result = new ConfigInfo();
    ProjectState state = resource.getControl().getProjectState();
    InheritedBooleanInfo useContributorAgreements = new InheritedBooleanInfo();
    InheritedBooleanInfo useSignedOffBy = new InheritedBooleanInfo();
    InheritedBooleanInfo useContentMerge = new InheritedBooleanInfo();
    InheritedBooleanInfo requireChangeId = new InheritedBooleanInfo();

    useContributorAgreements.value = state.isUseContributorAgreements();
    useSignedOffBy.value = state.isUseSignedOffBy();
    useContentMerge.value = state.isUseContentMerge();
    requireChangeId.value = state.isRequireChangeID();

    Project p = state.getProject();
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

    result.commentlinks = Maps.newLinkedHashMap();
    for (CommentLinkInfo cl : state.getCommentLinks()) {
      result.commentlinks.put(cl.name, cl);
    }

    result.theme = state.getTheme();
    return result;
  }

  public static class ConfigInfo {
    public final String kind = "gerritcodereview#project_config";

    public InheritedBooleanInfo useContributorAgreements;
    public InheritedBooleanInfo useContentMerge;
    public InheritedBooleanInfo useSignedOffBy;
    public InheritedBooleanInfo requireChangeId;

    public Map<String, CommentLinkInfo> commentlinks;
    public ThemeInfo theme;
  }

  public static class InheritedBooleanInfo {
    public Boolean value;
    public InheritableBoolean configuredValue;
    public Boolean inheritedValue;
  }
}
