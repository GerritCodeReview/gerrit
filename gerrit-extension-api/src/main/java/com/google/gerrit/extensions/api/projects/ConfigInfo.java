// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import java.util.List;
import java.util.Map;

public class ConfigInfo {
  public String description;
  public InheritedBooleanInfo useContributorAgreements;
  public InheritedBooleanInfo useContentMerge;
  public InheritedBooleanInfo useSignedOffBy;
  public InheritedBooleanInfo createNewChangeForAllNotInTarget;
  public InheritedBooleanInfo requireChangeId;
  public InheritedBooleanInfo enableSignedPush;
  public InheritedBooleanInfo requireSignedPush;
  public InheritedBooleanInfo rejectImplicitMerges;
  public InheritedBooleanInfo enableReviewerByEmail;
  public InheritedBooleanInfo matchAuthorToCommitterDate;
  public MaxObjectSizeLimitInfo maxObjectSizeLimit;
  public SubmitType submitType;
  public ProjectState state;
  public Map<String, Map<String, ConfigParameterInfo>> pluginConfig;
  public Map<String, ActionInfo> actions;

  public Map<String, CommentLinkInfo> commentlinks;
  public ThemeInfo theme;

  public Map<String, List<String>> extensionPanelNames;

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

  public static class ConfigParameterInfo {
    public String displayName;
    public String description;
    public String warning;
    public ProjectConfigEntryType type;
    public String value;
    public Boolean editable;
    public Boolean inheritable;
    public String configuredValue;
    public String inheritedValue;
    public List<String> permittedValues;
    public List<String> values;
  }
}
