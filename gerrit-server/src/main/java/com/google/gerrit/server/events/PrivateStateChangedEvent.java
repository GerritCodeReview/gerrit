
package com.google.gerrit.server.events;

import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.data.AccountAttribute;

public class PrivateStateChangedEvent extends ChangeEvent {
  static final String TYPE = "private-state-changed";
  public boolean isPrivate;
  public Supplier<AccountAttribute> changer;

  protected PrivateStateChangedEvent(Change change) {
    super(TYPE, change);
  }
}
