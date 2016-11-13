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

/** Named resource does not exist (HTTP 404 Not Found). */
public class ResourceNotFoundException extends RestApiException {
  private static final long serialVersionUID = 1L;

  /** Requested resource is not found, failing portion not specified. */
  public ResourceNotFoundException() {}

  public ResourceNotFoundException(String msg) {
    super(msg);
  }

  public ResourceNotFoundException(String msg, Throwable cause) {
    super(msg, cause);
  }

  /** @param id portion of the resource URI that does not exist. */
  public ResourceNotFoundException(IdString id) {
    super("Not found: " + id.get());
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResourceNotFoundException caching(CacheControl c) {
    return super.caching(c);
  }
}
