// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

/**
 * HTTP status code constants used across Gerrit and plugins.
 *
 * <p>Decoupled from any servlet-API namespace ({@code javax.servlet} / {@code jakarta.servlet}) and
 * from third-party HTTP-client libraries (Apache HttpClient's {@code org.apache.http.HttpStatus}).
 * Plugins importing these constants do not need to depend on a servlet API or pull in Apache
 * HttpClient just for the integer values.
 *
 * <p>Names retain the {@code SC_} prefix from the legacy {@code HttpServletResponse} constants for
 * drop-in source compatibility in static imports.
 */
public final class HttpStatusCode {
  // 2xx Success
  public static final int SC_OK = 200;
  public static final int SC_CREATED = 201;
  public static final int SC_ACCEPTED = 202;
  public static final int SC_NO_CONTENT = 204;
  public static final int SC_PARTIAL_CONTENT = 206;

  // 3xx Redirection
  public static final int SC_MOVED_PERMANENTLY = 301;
  public static final int SC_FOUND = 302;
  public static final int SC_SEE_OTHER = 303;
  public static final int SC_NOT_MODIFIED = 304;
  public static final int SC_TEMPORARY_REDIRECT = 307;
  public static final int SC_PERMANENT_REDIRECT = 308;

  // 4xx Client error
  public static final int SC_BAD_REQUEST = 400;
  public static final int SC_UNAUTHORIZED = 401;
  public static final int SC_FORBIDDEN = 403;
  public static final int SC_NOT_FOUND = 404;
  public static final int SC_METHOD_NOT_ALLOWED = 405;
  public static final int SC_NOT_ACCEPTABLE = 406;
  public static final int SC_REQUEST_TIMEOUT = 408;
  public static final int SC_CONFLICT = 409;
  public static final int SC_GONE = 410;
  public static final int SC_LENGTH_REQUIRED = 411;
  public static final int SC_PRECONDITION_FAILED = 412;
  public static final int SC_PAYLOAD_TOO_LARGE = 413;
  public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;
  public static final int SC_TOO_MANY_REQUESTS = 429;

  // 5xx Server error
  public static final int SC_INTERNAL_SERVER_ERROR = 500;
  public static final int SC_NOT_IMPLEMENTED = 501;
  public static final int SC_BAD_GATEWAY = 502;
  public static final int SC_SERVICE_UNAVAILABLE = 503;
  public static final int SC_GATEWAY_TIMEOUT = 504;

  private HttpStatusCode() {}
}
