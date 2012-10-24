// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Initialize the {@code auth} configuration section. */
@Singleton
class InitAuth implements InitStep {
  private final ConsoleUI ui;
  private final Section auth;

  @Inject
  InitAuth(final ConsoleUI ui, final Section.Factory sections) {
    this.ui = ui;
    this.auth = sections.get("auth", null);
  }

  public void run() {
    ui.header("User Authentication");
//  RealmConfigurationInitializer.init();

    if (auth.getSecure("registerEmailPrivateKey") == null) {
      auth.setSecure("registerEmailPrivateKey", SignedToken.generateRandomKey());
    }

    if (auth.getSecure("restTokenPrivateKey") == null) {
      auth.setSecure("restTokenPrivateKey", SignedToken.generateRandomKey());
    }
  }
}
