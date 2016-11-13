// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.extensions.restapi.RestApiException;

/**
 * Indicates that the commit is already contained in destination branch. Either the commit itself is
 * in the source tree, or the content is merged
 */
public class MergeIdenticalTreeException extends RestApiException {
  private static final long serialVersionUID = 1L;

  /** @param msg message to return to the client describing the error. */
  public MergeIdenticalTreeException(String msg) {
    super(msg);
  }
}
