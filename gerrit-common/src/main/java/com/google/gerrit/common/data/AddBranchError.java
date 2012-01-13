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

package com.google.gerrit.common.data;

public class AddBranchError extends RpcError<AddBranchError.Type> {

  public static enum Type {
    /** The branch cannot be created because the given branch name is invalid. */
    INVALID_NAME,

    /** The branch cannot be created because the given revision is invalid. */
    INVALID_REVISION,

    /**
     * The branch cannot be created under the given refname prefix (e.g branches
     * cannot be created under magic refname prefixes).
     */
    BRANCH_CREATION_NOT_ALLOWED_UNDER_REFNAME_PREFIX,

    /** The branch that should be created exists already. */
    BRANCH_ALREADY_EXISTS,

    /**
     * The branch cannot be created because it conflicts with an existing branch
     * (branches cannot be nested).
     */
    BRANCH_CREATION_CONFLICT
  }

  private final String refname;

  public AddBranchError(final AddBranchError.Type type, final String refname) {
    super(type);
    this.refname = refname;
  }

  public String getRefname() {
    return refname;
  }
}
