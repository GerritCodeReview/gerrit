// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.server.CurrentUser;

import org.eclipse.jgit.lib.Constants;


/**
 * Manages access control for Git refs.
 */
public class RefControl {

  private final ProjectControl projectControl;
  private final String refName;

  RefControl (final ProjectControl projectControl, final String refName) {
    this.projectControl = projectControl;
    this.refName = refName;
    System.err.println("refName:" + refName);
    System.err.println("User:" + projectControl.getCurrentUser().toString());
  }

  /**
   * Determines whether the user can submit a change to the ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can submit a change to
   * the Git ref
   */
  public boolean canUpload() {
    return true;
  }

  /**
   * Determines whether the user can create a new Git ref.
   *
   * @return {@code true} if the user specified can create a new Git ref
   */
  public boolean canCreate() {
    switch (getCurrentUser().getAccessPath()) {
      case WEB:
        if (projectControl.isOwner()) {
          return true;
        }
        if (isHead(refName)
            && projectControl.canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
          return true;
        }
        return false;

      case SSH:
        if (isHead(refName)
            && projectControl.canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
          return true;
        }
        if (isTag(refName) && projectControl.canPerform(PUSH_TAG, (short) 1)) {
          return true;
        }
        return false;

      default:
        return false;
    }
  }

  /**
   * Determines whether the user can delete the Git ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can delete a Git ref.
   */
  public boolean canDelete() {
    switch (getCurrentUser().getAccessPath()) {
      case WEB:
        if (projectControl.isOwner()) {
          return true;
        }
        if (isHead(refName)
            && projectControl.canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
          return true;
        }
        return false;

      case SSH:
        if (isHead(refName)
            && projectControl.canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
          return true;
        }
        return false;

      default:
        return false;
    }
  }

  private static boolean isHead(final String refName) {
    return refName.startsWith(Constants.R_HEADS);
  }

  private CurrentUser getCurrentUser() {
    return projectControl.getCurrentUser();
  }

  private static boolean isTag(final String refName) {
    return refName.startsWith(Constants.R_TAGS);
  }

}
