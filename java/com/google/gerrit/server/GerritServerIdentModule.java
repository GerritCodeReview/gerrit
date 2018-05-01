// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.TimeZone;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

public class GerritServerIdentModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(GerritPersonIdentFactory.class);
  }

  @Provides
  @Singleton
  GerritServerIdent getGerritServerIdent(@GerritServerConfig Config cfg) {
    return GerritServerIdent.create(cfg);
  }

  @Provides
  @Singleton
  TimeZone getTimeZone() {
    // Strictly speaking this may not be constant for the lifetime of the process since the
    // SystemReader could change, but in practice, many singletons elsewhere in Gerrit at the time
    // this code was written were storing the system timezone in an instance field, so they would
    // already be broken.
    //
    // We could of course make this provider a non-singleton, but callers would still be too tempted
    // to store the result indefinitely. It should be easier to debug problems when _no_ parts of
    // Gerrit respect a change in system timezone, rather than some parts respecting it and other
    // parts not. Hence, make this a singleton.
    return new PersonIdent("NOT_A_PERSON", "NOT_A_PERSON").getTimeZone();
  }
}
