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

/**
 * Resource state does not permit requested operation (HTTP 409 Conflict).
 *
 * <p>{@link RestModifyView} implementations may fail with this exception when the named resource
 * does not permit the modification to take place at this time. An example use is trying to abandon
 * a change that is already merged. The change cannot be abandoned once merged so an operation would
 * throw.
 */
public class ResourceConflictException extends RestApiException {
  private static final long serialVersionUID = 1L;

  /** @param msg message to return to the client describing the error. */
  public ResourceConflictException(String msg) {
    super(msg);
  }

  /** @param msg message to return to the client describing the error. */
  public ResourceConflictException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
