// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class RejectionReason {
  public enum MetricBucket {
    ACCOUNT_NOT_FOUND,
    CANNOT_ADD_PATCH_SET,
    CANNOT_COMBINE_NORMAL_AND_MAGIC_PUSHES,
    CANNOT_CREATE_REF_BECAUSE_IT_ALREADY_EXISTS,
    CANNOT_DELETE_CHANGES,
    CANNOT_DELETE_PROJECT_CONFIGURATION,
    CANNOT_EDIT_NEW_CHANGE,
    CANNOT_PUSH_MERGE_WITH_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
    CANNOT_SKIP_VALIDATION_FOR_MAGIC_PUSH,
    CANNOT_TOGGLE_WIP,
    CHANGE_IS_CLOSED,
    CHANGE_NOT_FOUND,
    CLIENT_ERROR,
    COMMIT_ALREADY_EXISTS_IN_CHANGE,
    COMMIT_ALREADY_EXISTS_IN_PROJECT,
    CONFLICT,
    BANNED_COMMIT,
    BRANCH_NOT_FOUND,
    DUPLICATE_CHANGE,
    DUPLICATE_CHANGE_ID,
    DUPLICATE_REQUEST,
    HELP_REQUESTED,
    IMPLICIT_MERGE,
    INTERNAL_SERVER_ERROR,
    INVALID_BASE,
    INVALID_BRANCH_SYNTAX,
    INVALID_CHANGE_ID,
    INVALID_DEADLINE,
    INVALID_HEAD,
    INVALID_OPTION,
    INVALID_PROJECT_CONFIGURATION_UPDATE,
    INVALID_REF,
    MISSING_REVISION,
    NO_COMMON_ANCESTRY,
    NO_NEW_CHANGES,
    NOT_A_COMMIT,
    NOT_MERGED_INTO_BRANCH,
    NOTEDB_UPDATE_WITHOUT_ACCESS_DATABASE_PERMISSION,
    NOTEDB_UPDATE_WITHOUT_ALLOW_OPTION,
    PATCH_SET_LOCKED,
    PROHIBITED,
    PROJECT_CONFIG_UPDATE_NOT_ALLOWED,
    PROJECT_NOT_WRITABLE,
    REF_NOT_FOUND,
    REJECTED_BY_VALIDATOR,
    REQUEST_CANCELLED,
    SIGNED_OFF_BY_REQUIRED,
    SUBMIT_ERROR,
    TOPIC_TOO_LARGE,
    TOO_MANY_CHANGES,
    TOO_MANY_COMMITS,
    UNKNOWN_COMMAND_TYPE;
  }

  static RejectionReason create(MetricBucket metricBucket, String why) {
    return new AutoValue_RejectionReason(metricBucket, why);
  }

  public abstract MetricBucket metricBucket();

  public abstract String why();
}
