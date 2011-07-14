// Copyright (C) 2009 The Android Open Source Project
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

enum CommitMergeStatus {
  /** */
  CLEAN_MERGE("Change has been successfully merged into the git repository."),

  /** */
  CLEAN_PICK("Change has been successfully cherry-picked"),

  /** */
  ALREADY_MERGED(""),

  /** */
  PATH_CONFLICT("Your change could not be merged due to a path conflict.\n"
                  + "\n"
                  + "Please merge (or rebase) the change locally and upload the resolution for review."),

  /** */
  MISSING_DEPENDENCY(""),

  /** */
  NO_PATCH_SET(""),

  /** */
  REVISION_GONE(""),

  /** */
  CRISS_CROSS_MERGE("Your change requires a recursive merge to resolve.\n"
                  + "\n"
                  + "Please merge (or rebase) the change locally and upload the resolution for review."),

  /** */
  CANNOT_CHERRY_PICK_ROOT("Cannot cherry-pick an initial commit onto an existing branch.\n"
                  + "\n"
                  + "Please merge the change locally and upload the merge commit for review."),

  /** */
  NOT_FAST_FORWARD("Project policy requires all submissions to be a fast-forward.\n"
                  + "\n"
                  + "Please rebase the change locally and upload again for review.");

  private String message;

  CommitMergeStatus(String message){
    this.message = message;
  }

  public String getMessage(){
    return this.message;
  }
}