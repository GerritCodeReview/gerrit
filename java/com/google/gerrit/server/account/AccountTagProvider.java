package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.List;

/**
 * An extension point for plugins to define their own account tags in addition to the ones defined
 * at {@link com.google.gerrit.extensions.common.AccountInfo.Tags}.
 */
@ExtensionPoint
public interface AccountTagProvider {
  List<String> getTags(Account.Id id);
}
