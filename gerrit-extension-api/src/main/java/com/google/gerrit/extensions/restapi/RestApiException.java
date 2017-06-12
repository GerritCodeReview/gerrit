// Copyright (C) 2012 The Android Open Source Project
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

/** Root exception type for JSON API failures. */
public class RestApiException extends Exception {
  private static final long serialVersionUID = 1L;
  private CacheControl caching = CacheControl.NONE;

  public RestApiException() {}

  public RestApiException(String msg) {
    super(msg);
  }

  public RestApiException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public CacheControl caching() {
    return caching;
  }

  @SuppressWarnings("unchecked")
  public <T extends RestApiException> T caching(CacheControl c) {
    caching = c;
    return (T) this;
  }
}
