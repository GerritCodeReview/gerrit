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

package com.google.gerrit.realm.development;

import com.google.gerrit.realm.RealmModule;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.Realm;
import com.google.inject.servlet.ServletModule;

public class DevelopmentRealmModule extends RealmModule {

  @Override
  protected void configure() {
    bind(Realm.class).to(DefaultRealm.class);
    installServletModule(new ServletModule() {
      @Override
      protected void configureServlets() {
        serve("/become").with(BecomeAnyAccountLoginServlet.class);
      }
    });
  }
}
