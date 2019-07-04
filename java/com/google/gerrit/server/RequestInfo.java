// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.logging.TraceContext;
import java.util.Optional;

/** Information about a request that was received from a user. */
@AutoValue
public abstract class RequestInfo {
  public enum RequestType {
    GIT_RECEIVE,
    GIT_UPLOAD,
    REST,
    SSH
  }

  /**
   * Type of the request, telling through which channel the request was coming in (REST, Git
   * receive, git upload, SSH).
   */
  public abstract RequestType requestType();

  /** The user that has sent the request. */
  public abstract CurrentUser callingUser();

  /** The trace context of the request. */
  public abstract TraceContext traceContext();

  /**
   * The name of the project for which the request is being done. Only available if the request is
   * tied to a project or change. If a project is available it's not guaranteed that it actually
   * exists (e.g. if a user made a request for a project that doesn't exist).
   */
  public abstract Optional<Project.NameKey> project();

  public static RequestInfo.Builder builder(
      RequestType requestType, CurrentUser callingUser, TraceContext traceContext) {
    return new AutoValue_RequestInfo.Builder()
        .requestType(requestType)
        .callingUser(callingUser)
        .traceContext(traceContext);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder requestType(RequestType requestType);

    public abstract Builder callingUser(CurrentUser callingUser);

    public abstract Builder traceContext(TraceContext traceContext);

    public abstract Builder project(Project.NameKey projectName);

    public abstract RequestInfo build();
  }
}
