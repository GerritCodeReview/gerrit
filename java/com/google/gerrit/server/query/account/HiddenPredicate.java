package com.google.gerrit.server.query.account;

import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.account.AccountState;

public class HiddenPredicate extends PostFilterPredicate<AccountState> {

  private static boolean hidden;

  HiddenPredicate(boolean hidden) {
    super(AccountQueryBuilder.FIELD_HIDDEN, hidden ? "1" : "0");
    this.hidden = hidden;
  }

  @Override
  public boolean match(AccountState accountState) {
    return accountState.account().isHidden().orElse(false).equals(hidden);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
