package com.google.gerrit.server;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import java.sql.Timestamp;

/** Change to an assignee's status. */
@AutoValue
public abstract class AssigneeStatusUpdate {
  public static AssigneeStatusUpdate create(
      Timestamp ts, Account.Id updatedBy, Account.Id pastAssignee, Account.Id currentAssignee) {
    return new AutoValue_AssigneeStatusUpdate(ts, updatedBy, pastAssignee, currentAssignee);
  }

  public abstract Timestamp date();

  public abstract Account.Id updatedBy();

  @Nullable
  public abstract Account.Id pastAssignee();

  @Nullable
  public abstract Account.Id currentAssignee();
}
