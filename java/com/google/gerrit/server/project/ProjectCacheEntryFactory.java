// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

// This factory class primarily exists to ensure that we never end up passing thick objects to the
// ProjectCacheEntry constructor.
@VisibleForTesting
@Singleton
public class ProjectCacheEntryFactory {
  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final CapabilityCollection.Factory capabilityCollectionFactory;

  @Inject
  ProjectCacheEntryFactory(
      AllProjectsName allProjectsName,
      AllUsersName allUsersName,
      CapabilityCollection.Factory capabilityCollectionFactory) {
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.capabilityCollectionFactory = capabilityCollectionFactory;
  }

  // All arguments to this method must be safe to pass to the ProjectCacheEntry constructor.
  ProjectCacheEntry create(ProjectConfig config) {
    boolean isAllProjects = config.getProject().getNameKey().equals(allProjectsName);
    return new ProjectCacheEntry(
        config,
        isAllProjects,
        config.getProject().getNameKey().equals(allUsersName),
        isAllProjects
            ? capabilityCollectionFactory.create(
                config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES))
            : null);
  }
}
