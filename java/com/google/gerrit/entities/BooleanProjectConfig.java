// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.gerrit.common.Nullable;

/**
 * Contains all inheritable boolean project configs and maps internal representations to API
 * objects.
 *
 * <p>Perform the following steps for adding a new inheritable boolean project config:
 *
 * <ol>
 *   <li>Add a field to {@link com.google.gerrit.extensions.api.projects.ConfigInput}
 *   <li>Add a field to {@link com.google.gerrit.extensions.api.projects.ConfigInfo}
 *   <li>Add the config to this enum
 *   <li>Add API mappers to {@link
 *       com.google.gerrit.server.project.BooleanProjectConfigTransformations}
 * </ol>
 */
public enum BooleanProjectConfig {
  USE_CONTRIBUTOR_AGREEMENTS("receive", "requireContributorAgreement"),
  USE_SIGNED_OFF_BY("receive", "requireSignedOffBy"),
  USE_CONTENT_MERGE("submit", "mergeContent"),
  REQUIRE_CHANGE_ID("receive", "requireChangeId"),
  CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET("receive", "createNewChangeForAllNotInTarget"),
  ENABLE_SIGNED_PUSH("receive", "enableSignedPush"),
  REQUIRE_SIGNED_PUSH("receive", "requireSignedPush"),
  REJECT_IMPLICIT_MERGES("receive", "rejectImplicitMerges"),
  PRIVATE_BY_DEFAULT("change", "privateByDefault"),
  ENABLE_REVIEWER_BY_EMAIL("reviewer", "enableByEmail"),
  MATCH_AUTHOR_TO_COMMITTER_DATE("submit", "matchAuthorToCommitterDate"),
  REJECT_EMPTY_COMMIT("submit", "rejectEmptyCommit"),
  WORK_IN_PROGRESS_BY_DEFAULT("change", "workInProgressByDefault"),
  SKIP_ADDING_AUTHOR_AND_COMMITTER_AS_REVIEWERS(
      "reviewer", "skipAddingAuthorAndCommitterAsReviewers");

  // Git config
  private final String section;
  private final String name;

  BooleanProjectConfig(String section, String name) {
    this.section = section;
    this.name = name;
  }

  public String getSection() {
    return section;
  }

  @Nullable
  public String getSubSection() {
    return null;
  }

  public String getName() {
    return name;
  }
}
