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

import com.google.gerrit.extensions.client.SubmitType;

/** Describes the submit type for a change. */
public class SubmitTypeRecord {
  public enum Status {
    /** The type was computed successfully */
    OK,

    /**
     * An internal server error occurred preventing computation.
     *
     * <p>Additional detail may be available in {@link SubmitTypeRecord#errorMessage}
     */
    RULE_ERROR
  }

  public static SubmitTypeRecord OK(SubmitType type) {
    return new SubmitTypeRecord(Status.OK, type, null);
  }

  public static SubmitTypeRecord error(String err) {
    return new SubmitTypeRecord(SubmitTypeRecord.Status.RULE_ERROR, null, err);
  }

  /** Status enum value of the record. */
  public final Status status;

  /** Submit type of the record; never null if {@link #status} is {@code OK}. */
  public final SubmitType type;

  /** Submit type of the record; always null if {@link #status} is {@code OK}. */
  public final String errorMessage;

  private SubmitTypeRecord(Status status, SubmitType type, String errorMessage) {
    this.status = status;
    this.type = type;
    this.errorMessage = errorMessage;
  }

  public boolean isOk() {
    return status == Status.OK;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(status);
    if (status == Status.RULE_ERROR && errorMessage != null) {
      sb.append('(').append(errorMessage).append(")");
    }
    if (type != null) {
      sb.append('[');
      sb.append(type.name());
      sb.append(']');
    }
    return sb.toString();
  }
}
