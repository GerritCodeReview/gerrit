// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Change;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from performing a review (comment, abandon, etc.)
 */
public class ReviewResult {
  protected List<Error> errors;
  protected Change.Id changeId;

  public ReviewResult() {
    errors = new ArrayList<Error>();
  }

  public void addError(final Error e) {
    errors.add(e);
  }

  public List<Error> getErrors() {
    return errors;
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  public void setChangeId(Change.Id changeId) {
    this.changeId = changeId;
  }

  public static class Error {
    public static enum Type {
      /** Not permitted to abandon this change. */
      ABANDON_NOT_PERMITTED,

      /** Not permitted to restore this change. */
      RESTORE_NOT_PERMITTED,

      /** Not permitted to submit this change. */
      SUBMIT_NOT_PERMITTED,

      /** Approvals or dependencies are lacking for submission. */
      SUBMIT_NOT_READY,

      /** Review operation invalid because change is closed. */
      CHANGE_IS_CLOSED,

      /** Not permitted to publish this draft patch set */
      PUBLISH_NOT_PERMITTED,

      /** Not permitted to delete this draft patch set */
      DELETE_NOT_PERMITTED,

      /** Review operation not permitted by rule. */
      RULE_ERROR,

      /** Review operation invalid because patch set is not a draft. */
      NOT_A_DRAFT,

      /** Error writing change to git repository */
      GIT_ERROR,

      /** The destination branch does not exist */
      DEST_BRANCH_NOT_FOUND
    }

    protected Type type;
    protected String message;

    protected Error() {
    }

    public Error(final Type type) {
      this.type = type;
      this.message = null;
    }

    public Error(final Type type, final String message) {
      this.type = type;
      this.message = message;
    }

    public Type getType() {
      return type;
    }

    public String getMessage() {
      return message;
    }

    public String getMessageOrType() {
      if (message != null) {
        return message;
      }
      return "" + type;
    }

    @Override
    public String toString() {
      String ret = type + "";
      if (message != null) {
        ret += " " + message;
      }
      return ret;
    }
  }
}
