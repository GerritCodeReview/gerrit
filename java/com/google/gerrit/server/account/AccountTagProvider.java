package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import java.util.List;

public interface AccountTagProvider {
  List<String> getTags(Account.Id id);
}