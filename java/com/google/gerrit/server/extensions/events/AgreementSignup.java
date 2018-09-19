// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.AgreementSignupListener;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class AgreementSignup {
  private final PluginSetContext<AgreementSignupListener> listeners;
  private final EventUtil util;

  @Inject
  AgreementSignup(PluginSetContext<AgreementSignupListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  public void fire(AccountState accountState, String agreementName) {
    if (listeners.isEmpty()) {
      return;
    }
    Event event = new Event(util.accountInfo(accountState), agreementName);
    listeners.runEach(l -> l.onAgreementSignup(event));
  }

  private static class Event extends AbstractNoNotifyEvent
      implements AgreementSignupListener.Event {
    private final AccountInfo account;
    private final String agreementName;

    Event(AccountInfo account, String agreementName) {
      this.account = account;
      this.agreementName = agreementName;
    }

    @Override
    public AccountInfo getAccount() {
      return account;
    }

    @Override
    public String getAgreementName() {
      return agreementName;
    }
  }
}
