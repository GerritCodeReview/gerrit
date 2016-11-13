// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gwt.http.client.Response;

/** Wraps decoded server reply with HTTP headers. */
public class HttpResponse<T> {
  private final Response httpResponse;
  private final String contentType;
  private final T result;

  HttpResponse(Response httpResponse, String contentType, T result) {
    this.httpResponse = httpResponse;
    this.contentType = contentType;
    this.result = result;
  }

  /** HTTP status code, always in the 2xx family. */
  public int getStatusCode() {
    return httpResponse.getStatusCode();
  }

  /**
   * Content type supplied by the server.
   *
   * <p>This helper simplifies the common {@code getHeader("Content-Type")} case.
   */
  public String getContentType() {
    return contentType;
  }

  /** Lookup an arbitrary reply header. */
  public String getHeader(String header) {
    if ("Content-Type".equals(header)) {
      return contentType;
    }
    return httpResponse.getHeader(header);
  }

  public T getResult() {
    return result;
  }
}
