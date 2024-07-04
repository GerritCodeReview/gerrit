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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_TIMEOUT;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class RejectionReason {
  private static final int SC_CLIENT_CLOSED_REQUEST = 499;

  public enum MetricBucket {
    ACCOUNT_NOT_FOUND(SC_NOT_FOUND),
    CANNOT_ADD_PATCH_SET(SC_FORBIDDEN),
    CANNOT_COMBINE_NORMAL_AND_MAGIC_PUSHES(SC_BAD_REQUEST),
    CANNOT_CREATE_REF_BECAUSE_IT_ALREADY_EXISTS(SC_CONFLICT),
    CANNOT_DELETE_CHANGES(SC_METHOD_NOT_ALLOWED),
    CANNOT_DELETE_PROJECT_CONFIGURATION(SC_METHOD_NOT_ALLOWED),
    CANNOT_EDIT_NEW_CHANGE(SC_CONFLICT),
    CANNOT_PUSH_MERGE_WITH_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET(SC_BAD_REQUEST),
    CANNOT_SKIP_VALIDATION_FOR_MAGIC_PUSH(SC_BAD_REQUEST),
    CANNOT_TOGGLE_WIP(SC_FORBIDDEN),
    CHANGE_IS_CLOSED(SC_CONFLICT),
    CHANGE_NOT_FOUND(SC_NOT_FOUND),
    CLIENT_CLOSED_REQUEST(SC_CLIENT_CLOSED_REQUEST),
    CLIENT_ERROR(SC_BAD_REQUEST),
    CLIENT_PROVIDED_DEADLINE_EXCEEDED(SC_REQUEST_TIMEOUT),
    COMMIT_ALREADY_EXISTS_IN_CHANGE(SC_CONFLICT),
    COMMIT_ALREADY_EXISTS_IN_PROJECT(SC_CONFLICT),
    CONFLICT(SC_CONFLICT),
    BANNED_COMMIT(SC_CONFLICT),
    BRANCH_NOT_FOUND(SC_NOT_FOUND),
    DUPLICATE_CHANGE(SC_BAD_REQUEST),
    DUPLICATE_CHANGE_ID(SC_BAD_REQUEST),
    DUPLICATE_REQUEST(SC_BAD_REQUEST),
    HELP_REQUESTED(SC_OK),
    IMPLICIT_MERGE(SC_BAD_REQUEST),
    INTERNAL_SERVER_ERROR(SC_INTERNAL_SERVER_ERROR),
    INVALID_BASE(SC_BAD_REQUEST),
    INVALID_BRANCH_SYNTAX(SC_BAD_REQUEST),
    INVALID_CHANGE_ID(SC_BAD_REQUEST),
    INVALID_DEADLINE(SC_BAD_REQUEST),
    INVALID_HEAD(SC_BAD_REQUEST),
    INVALID_OPTION(SC_BAD_REQUEST),
    INVALID_PROJECT_CONFIGURATION_UPDATE(SC_BAD_REQUEST),
    INVALID_REF(SC_BAD_REQUEST),
    MISSING_REVISION(SC_INTERNAL_SERVER_ERROR),
    NO_COMMON_ANCESTRY(SC_BAD_REQUEST),
    NO_NEW_CHANGES(SC_BAD_REQUEST),
    NOT_A_COMMIT(SC_BAD_REQUEST),
    NOT_MERGED_INTO_BRANCH(SC_BAD_REQUEST),
    NOTEDB_UPDATE_WITHOUT_ACCESS_DATABASE_PERMISSION(SC_FORBIDDEN),
    NOTEDB_UPDATE_WITHOUT_ALLOW_OPTION(SC_BAD_REQUEST),
    PATCH_SET_LOCKED(SC_CONFLICT),
    PROHIBITED(SC_FORBIDDEN),
    PROJECT_CONFIG_UPDATE_NOT_ALLOWED(SC_FORBIDDEN),
    PROJECT_NOT_WRITABLE(SC_CONFLICT),
    REF_NOT_FOUND(SC_NOT_FOUND),
    REJECTED_BY_VALIDATOR(SC_BAD_REQUEST),
    SERVER_DEADLINE_EXCEEDED(SC_INTERNAL_SERVER_ERROR),
    SIGNED_OFF_BY_REQUIRED(SC_BAD_REQUEST),
    SUBMIT_ERROR(SC_INTERNAL_SERVER_ERROR),
    TOPIC_TOO_LARGE(SC_BAD_REQUEST),
    TOO_MANY_CHANGES(SC_BAD_REQUEST),
    TOO_MANY_COMMITS(SC_BAD_REQUEST),
    UNKNOWN_COMMAND_TYPE(SC_BAD_REQUEST);

    private final int statusCode;

    private MetricBucket(int statusCode) {
      this.statusCode = statusCode;
    }

    public int statusCode() {
      return statusCode;
    }
  }

  static RejectionReason create(MetricBucket metricBucket, String why) {
    return new AutoValue_RejectionReason(metricBucket, why);
  }

  public abstract MetricBucket metricBucket();

  public abstract String why();
}
