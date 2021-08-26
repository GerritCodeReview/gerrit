package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.List;

@ExtensionPoint
public interface AccountTagProvider {
  List<String> getTags(Account.Id id);
}