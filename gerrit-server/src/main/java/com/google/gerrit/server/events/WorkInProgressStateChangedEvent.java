
package com.google.gerrit.server.events;

import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.data.AccountAttribute;

public class WorkInProgressStateChangedEvent extends ChangeEvent {
  static final String TYPE = "wip-state-changed";
  public boolean isWip;
  public Supplier<AccountAttribute> changer;

  protected WorkInProgressStateChangedEvent(Change change) {
    super(TYPE, change);
  }
}
