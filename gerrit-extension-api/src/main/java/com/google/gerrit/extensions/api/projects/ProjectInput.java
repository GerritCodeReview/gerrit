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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import java.util.List;
import java.util.Map;

public class ProjectInput {
  public String name;
  public String parent;
  public String description;
  public boolean permissionsOnly;
  public boolean createEmptyCommit;
  public SubmitType submitType;
  public List<String> branches;
  public List<String> owners;
  public InheritableBoolean useContributorAgreements;
  public InheritableBoolean useSignedOffBy;
  public InheritableBoolean useContentMerge;
  public InheritableBoolean requireChangeId;
  public InheritableBoolean createNewChangeForAllNotInTarget;
  public String maxObjectSizeLimit;
  public Map<String, Map<String, ConfigValue>> pluginConfigValues;
}
