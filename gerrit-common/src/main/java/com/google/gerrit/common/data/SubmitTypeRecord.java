package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Project;

/**
 * Describes the submit action for a change.
 */
public class SubmitTypeRecord {
  public static enum Status {
    /** The action was computed successfully */
    OK,

    /** An internal server error occurred preventing computation.
     * <p>
     * Additional detail may be available in {@link SubmitActionRecord#errorMessage}
     */
    RULE_ERROR
  }

  public static SubmitTypeRecord OK(Project.SubmitType type) {
    SubmitTypeRecord r = new SubmitTypeRecord();
    r.status = Status.OK;
    r.type = type;
    return r;
  }

  public Status status;
  public Project.SubmitType type;
  public String errorMessage;

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
