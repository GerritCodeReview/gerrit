// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import com.google.gerrit.extensions.restapi.ResourceConflictException;

/**
 * Exception to be thrown if any submodule operation is not possible due to conflicts.
 *
 * <p>Throwing this exception results in a {@code 409 Conflict} response to the calling user. The
 * exception message is returned as error message to the user.
 */
public class SubmoduleConflictException extends ResourceConflictException {
  private static final long serialVersionUID = 1L;

  public SubmoduleConflictException(String msg) {
  super(msg);
  }
}
