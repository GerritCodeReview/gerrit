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

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.actions.ActionInfo;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.TransferConfig;
import com.google.inject.util.Providers;

import java.util.Map;

public class ConfigInfo {
  public final String kind = "gerritcodereview#project_config";

  public String description;
  public InheritedBooleanInfo useContributor_agreements;
  public InheritedBooleanInfo use_content_merge;
  public InheritedBooleanInfo use_signed_off_by;
  public InheritedBooleanInfo require_change_id;
  public MaxObjectSizeLimitInfo max_object_size_limit;
  public SubmitType submit_type;
  public Project.State state;
  public Map<String, ActionInfo> actions;

  public Map<String, CommentLinkInfo> commentlinks;
  public ThemeInfo theme;

  public ConfigInfo(ProjectControl control,
      TransferConfig config,
      DynamicMap<RestView<ProjectResource>> views) {
    ProjectState projectState = control.getProjectState();
    Project p = control.getProject();
    this.description = Strings.emptyToNull(p.getDescription());

    InheritedBooleanInfo useContributorAgreements =
        new InheritedBooleanInfo();
    InheritedBooleanInfo useSignedOffBy = new InheritedBooleanInfo();
    InheritedBooleanInfo useContentMerge = new InheritedBooleanInfo();
    InheritedBooleanInfo requireChangeId = new InheritedBooleanInfo();

    useContributorAgreements.value = projectState.isUseContributorAgreements();
    useSignedOffBy.value = projectState.isUseSignedOffBy();
    useContentMerge.value = projectState.isUseContentMerge();
    requireChangeId.value = projectState.isRequireChangeID();

    useContributorAgreements.configured_value =
        p.getUseContributorAgreements();
    useSignedOffBy.configured_value = p.getUseSignedOffBy();
    useContentMerge.configured_value = p.getUseContentMerge();
    requireChangeId.configured_value = p.getRequireChangeID();

    ProjectState parentState = Iterables.getFirst(projectState
        .parents(), null);
    if (parentState != null) {
      useContributorAgreements.inherited_value =
          parentState.isUseContributorAgreements();
      useSignedOffBy.inherited_value = parentState.isUseSignedOffBy();
      useContentMerge.inherited_value = parentState.isUseContentMerge();
      requireChangeId.inherited_value = parentState.isRequireChangeID();
    }

    this.useContributor_agreements = useContributorAgreements;
    this.use_signed_off_by = useSignedOffBy;
    this.use_content_merge = useContentMerge;
    this.require_change_id = requireChangeId;

    MaxObjectSizeLimitInfo maxObjectSizeLimit = new MaxObjectSizeLimitInfo();
    maxObjectSizeLimit.value =
        config.getEffectiveMaxObjectSizeLimit(projectState) == config
            .getMaxObjectSizeLimit() ? config
            .getFormattedMaxObjectSizeLimit() : p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.configured_value = p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.inherited_value =
        config.getFormattedMaxObjectSizeLimit();
    this.max_object_size_limit = maxObjectSizeLimit;

    this.submit_type = p.getSubmitType();
    this.state = p.getState() != Project.State.ACTIVE ? p.getState() : null;

    this.commentlinks = Maps.newLinkedHashMap();
    for (CommentLinkInfo cl : projectState.getCommentLinks()) {
      this.commentlinks.put(cl.name, cl);
    }

    actions = Maps.newTreeMap();
    for (UiAction.Description d : UiActions.from(
        views, new ProjectResource(control),
        Providers.of(control.getCurrentUser()))) {
      actions.put(d.getId(), new ActionInfo(d));
    }
    this.theme = projectState.getTheme();
  }

  public static class InheritedBooleanInfo {
    public Boolean value;
    public InheritableBoolean configured_value;
    public Boolean inherited_value;
  }

  public static class MaxObjectSizeLimitInfo {
    public String value;
    public String configured_value;
    public String inherited_value;
  }
}
