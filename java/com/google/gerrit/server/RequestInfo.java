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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.logging.TraceContext;
import java.util.Optional;

/** Information about a request that was received from a user. */
@AutoValue
public abstract class RequestInfo {
  /** Channel through which a user request was received. */
  public enum RequestType {
    /** request type for git push */
    GIT_RECEIVE,

    /** request type for git fetch */
    GIT_UPLOAD,

    /** request type for call to REST API */
    REST,

    /** request type for call to SSH API */
    SSH
  }

  /**
   * Type of the request, telling through which channel the request was coming in.
   *
   * <p>See {@link RequestType} for the types that are used by Gerrit core. Other request types are
   * possible, e.g. if a plugin supports receiving requests through another channel.
   */
  public abstract String requestType();

  /**
   * Request URI.
   *
   * <p>Only set if request type is {@link RequestType#REST}.
   *
   * <p>Never includes the "/a" prefix.
   *
   * <p>Doesn't include the query string with the request parameters (see {@link
   * #requestQueryString()}.
   */
  public abstract Optional<String> requestUri();

  /**
   * Request query string that contains the request parameters.
   *
   * <p>Only set if request type is {@link RequestType#REST}.
   */
  public abstract Optional<String> requestQueryString();

  /** Request headers in the form '{@code <header-name>:<header-value>}'. */
  public abstract ImmutableList<String> headers();

  /**
   * Redacted request URI.
   *
   * <p>Request URI where resource IDs are replaced by '*'.
   */
  @Memoized
  public Optional<String> redactedRequestUri() {
    return requestUri().map(RequestInfo::redactRequestUri);
  }

  /**
   * The command name of the SSH command.
   *
   * <p>Only set if request type is {@link RequestType#SSH}.
   */
  public abstract Optional<String> commandName();

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

  @Memoized
  public String formatForLogging() {
    StringBuilder sb = new StringBuilder();
    sb.append(requestType());
    redactedRequestUri().ifPresent(redactedRequestUri -> sb.append(' ').append(redactedRequestUri));
    return sb.toString();
  }

  /**
   * Redacts resource IDs from the given request URI.
   *
   * <p>resource IDs in the request URI are replaced with '*'.
   *
   * @param requestUri a REST URI that has path segments that alternate between view name and
   *     resource IDs (e.g. "/<view>", "/<view>/<id>", "/<view>/<id>/<view>",
   *     "/<view>/<id>/<view>/<id>", "/<view>/<id>/<view>/<id>/<view>" etc.), must be given without
   *     the '/a' prefix
   * @return the redacted request URI
   */
  static String redactRequestUri(String requestUri) {
    requireNonNull(requestUri, "requestUri");
    checkState(
        !requestUri.startsWith("/a/"), "request URI must not start with '/a/': %s", requestUri);

    StringBuilder redactedRequestUri = new StringBuilder();

    boolean hasLeadingSlash = false;
    boolean hasTrailingSlash = false;
    if (requestUri.startsWith("/")) {
      hasLeadingSlash = true;
      requestUri = requestUri.substring(1);
    }
    if (requestUri.endsWith("/")) {
      hasTrailingSlash = true;
      requestUri = requestUri.substring(0, requestUri.length() - 1);
    }

    boolean idPathSegment = false;
    for (String pathSegment : Splitter.on('/').split(requestUri)) {
      if (!idPathSegment) {
        redactedRequestUri.append("/" + pathSegment);
        idPathSegment = true;
      } else {
        redactedRequestUri.append("/");
        if (!pathSegment.isEmpty()) {
          redactedRequestUri.append("*");
        }
        idPathSegment = false;
      }
    }

    if (!hasLeadingSlash) {
      redactedRequestUri.deleteCharAt(0);
    }
    if (hasTrailingSlash) {
      redactedRequestUri.append('/');
    }

    return redactedRequestUri.toString();
  }

  public static RequestInfo.Builder builder(
      RequestType requestType, CurrentUser callingUser, TraceContext traceContext) {
    return builder().requestType(requestType).callingUser(callingUser).traceContext(traceContext);
  }

  public static RequestInfo.Builder builder(
      RequestType requestType,
      String commandName,
      CurrentUser callingUser,
      TraceContext traceContext) {
    return builder()
        .requestType(requestType)
        .commandName(commandName)
        .callingUser(callingUser)
        .traceContext(traceContext);
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public static RequestInfo.Builder builder() {
    return new AutoValue_RequestInfo.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder requestType(String requestType);

    public Builder requestType(RequestType requestType) {
      return requestType(requestType.name());
    }

    public abstract Builder requestUri(String requestUri);

    public abstract Builder requestQueryString(String requestQueryString);

    /** Gets a builder for adding reasons for this status. */
    abstract ImmutableList.Builder<String> headersBuilder();

    /** Adds a header. */
    @CanIgnoreReturnValue
    public Builder addHeader(String headerName, String headerValue) {
      headersBuilder().add(headerName + "=" + headerValue);
      return this;
    }

    public abstract Builder commandName(String commandName);

    public abstract Builder callingUser(CurrentUser callingUser);

    public abstract Builder traceContext(TraceContext traceContext);

    public abstract Builder project(Project.NameKey projectName);

    public abstract RequestInfo build();
  }
}
