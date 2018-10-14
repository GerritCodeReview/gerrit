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
import java.util.Map;

public class ConfigInput {
  public String description;
  public InheritableBoolean useContributorAgreements;
  public InheritableBoolean useContentMerge;
  public InheritableBoolean useSignedOffBy;
  public InheritableBoolean createNewChangeForAllNotInTarget;
  public InheritableBoolean requireChangeId;
  public InheritableBoolean enableSignedPush;
  public InheritableBoolean requireSignedPush;
  public InheritableBoolean rejectImplicitMerges;
  public InheritableBoolean privateByDefault;
  public InheritableBoolean workInProgressByDefault;
  public InheritableBoolean enableReviewerByEmail;
  public InheritableBoolean matchAuthorToCommitterDate;
  public InheritableBoolean rejectEmptyCommit;
  public String maxObjectSizeLimit;
  public SubmitType submitType;
  public ProjectState state;
  public Map<String, Map<String, ConfigValue>> pluginConfigValues;
  public Map<String, CommentLinkInput> commentLinks;
}
