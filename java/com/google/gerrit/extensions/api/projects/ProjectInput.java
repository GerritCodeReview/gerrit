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
  public InheritableBoolean rejectEmptyCommit;
  public InheritableBoolean enableSignedPush;
  public InheritableBoolean requireSignedPush;
  public String maxObjectSizeLimit;
  public Map<String, Map<String, ConfigValue>> pluginConfigValues;

  /**
   * If set, only the project initialization is being (re-)done and the repository creation is
   * skipped.
   *
   * <p>The project initialization consists out of setting {@code HEAD}, creating the {@code
   * project.config} file in {@code refs/meta/config} and creating initial branches with empty
   * commits.
   *
   * <p>This is useful to retry the project initialization after a create project request has failed
   * and as a result the repository was created but the project initialization was not done.
   *
   * <p>Note, that this does not override any existing project configuration.
   *
   * <p>If a conflicting configuration already exists the request is rejected with `409 Conflict`.
   */
  public boolean initOnly;
}
