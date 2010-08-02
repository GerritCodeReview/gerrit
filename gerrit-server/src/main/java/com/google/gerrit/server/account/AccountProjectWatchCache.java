package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Project;

import java.util.List;

public interface AccountProjectWatchCache {
  public List<AccountProjectWatch> byAccount(Account.Id id);

  public List<AccountProjectWatch> byProject(Project.NameKey name);

  public void evict(AccountProjectWatch.Key key);
}
