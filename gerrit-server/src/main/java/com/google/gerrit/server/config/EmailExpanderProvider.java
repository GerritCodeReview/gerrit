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

package com.google.gerrit.server.config;

import com.google.gerrit.server.account.EmailExpander;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

class EmailExpanderProvider implements Provider<EmailExpander> {
  private final EmailExpander expander;

  @Inject
  EmailExpanderProvider(@GerritServerConfig final Config cfg) {
    final String s = cfg.getString("auth", null, "emailformat");
    if (EmailExpander.Simple.canHandle(s)) {
      expander = new EmailExpander.Simple(s);

    } else if (EmailExpander.None.canHandle(s)) {
      expander = EmailExpander.None.INSTANCE;

    } else {
      throw new IllegalArgumentException("Invalid auth.emailformat: " + s);
    }
  }

  @Override
  public EmailExpander get() {
    return expander;
  }
}
