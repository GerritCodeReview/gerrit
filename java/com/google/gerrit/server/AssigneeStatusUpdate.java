package com.google.gerrit.server;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Account;
import java.sql.Timestamp;
import java.util.Optional;

/** Change to an assignee's status. */
@AutoValue
public abstract class AssigneeStatusUpdate {
  public static AssigneeStatusUpdate create(
      Timestamp ts, Account.Id updatedBy, Optional<Account.Id> currentAssignee) {
    return new AutoValue_AssigneeStatusUpdate(ts, updatedBy, currentAssignee);
  }

  public abstract Timestamp date();

  public abstract Account.Id updatedBy();

  public abstract Optional<Account.Id> currentAssignee();
}
