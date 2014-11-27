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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class RepositoryConfig {

  static final String SECTION_NAME = "repository";
  static final String OWNER_GROUP_NAME = "ownerGroup";
  static final String DEFAULT_SUBMIT_TYPE_NAME = "defaultSubmitType";

  private final Config cfg;

  @Inject
  public RepositoryConfig(@GerritServerConfig final Config cfg) {
    this.cfg = cfg;
  }

  public SubmitType getDefaultSubmitType(Project.NameKey project) {
    return cfg.getEnum(SECTION_NAME, findSubSection(project.get()),
        DEFAULT_SUBMIT_TYPE_NAME, SubmitType.MERGE_IF_NECESSARY);
  }

  public String[] getOwnerGroups(Project.NameKey project) {
    return cfg.getStringList(SECTION_NAME, findSubSection(project.get()),
        OWNER_GROUP_NAME);
  }

  /**
   * Find the subSection to get repository configuration from.
   * <p>
   * SubSection can use the * pattern so if project name match more than one
   * section, return the more precise one. E.g if the following subSections are
   * defined:
   *
   * <pre>
   * [repository "somePath/*"]
   *   name = value
   * [repository "somePath/somePath/*"]
   *   name = value
   * </pre>
   *
   * and this method is called with "somePath/somePath/someProject" as project
   * name, it will return the subSection "somePath/somePath/*"
   *
   * @param project Name of the project
   * @return the name of the subSection, null if none is found
   */
  private String findSubSection(String project) {
    String subSectionFound = null;
    for (String subSection : cfg.getSubsections(SECTION_NAME)) {
      if (isMatch(subSection, project)
          && (subSectionFound == null || subSectionFound.length() < subSection
              .length())) {
        subSectionFound = subSection;
      }
    }
    return subSectionFound;
  }

  private boolean isMatch(String subSection, String project) {
    return project.equals(subSection)
        || (subSection.endsWith("*") && project.startsWith(subSection
            .substring(0, subSection.length() - 1)));
  }
}
