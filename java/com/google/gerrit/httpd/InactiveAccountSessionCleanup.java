package com.google.gerrit.httpd;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.events.AccountActivationListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.Inject;

public class InactiveAccountSessionCleanup implements AccountActivationListener {
  private final DynamicItem<WebSessionCleaner> webSessionCleaner;

  @Inject
  public InactiveAccountSessionCleanup(DynamicItem<WebSessionCleaner> webSessionCleaner) {
    this.webSessionCleaner = webSessionCleaner;
  }

  @Override
  public void onAccountDeactivated(int id) {
    webSessionCleaner.get().cleanUp(Account.id(id));
  }
}
