// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.become;

import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.RealmExtension;
import com.google.gerrit.server.config.RealmCacheModule;
import com.google.inject.servlet.ServletModule;

public class BecomeAnyAccountRealmExtension implements RealmExtension {

  @Override
  public String getName() {
    return "DEVELOPMENT_BECOME_ANY_ACCOUNT";
  }

  @Override
  public Class<? extends Realm> getRealm() {
    return DefaultRealm.class;
  }

  @Override
  public RealmCacheModule getCacheModule() {
    return RealmExtension.EMPTY_SERVER_MODULE;
  }

  @Override
  public ServletModule getWebModule() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        serve("/become").with(BecomeAnyAccountLoginServlet.class);
      }
    };
  }
}
