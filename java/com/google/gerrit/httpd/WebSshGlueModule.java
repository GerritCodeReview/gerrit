// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Pulls objects from the SSH injector over the HTTP injector.
 *
 * <p>This mess is only necessary because we build up two different injectors, in order to have
 * different request scopes. But some HTTP RPCs can cause changes to the SSH side of the house, and
 * thus needs access to it.
 */
public class WebSshGlueModule extends AbstractModule {
  private final Provider<SshInfo> sshInfoProvider;

  @Inject
  WebSshGlueModule(Provider<SshInfo> sshInfoProvider) {
    this.sshInfoProvider = sshInfoProvider;
  }

  @Override
  protected void configure() {
    bind(SshInfo.class).toProvider(sshInfoProvider);
  }
}
