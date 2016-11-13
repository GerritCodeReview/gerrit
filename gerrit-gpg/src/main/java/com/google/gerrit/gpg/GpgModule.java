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

package com.google.gerrit.gpg;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.gpg.api.GpgApiModule;
import com.google.gerrit.server.EnableSignedPush;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GpgModule extends FactoryModule {
  private static final Logger log = LoggerFactory.getLogger(GpgModule.class);

  private final Config cfg;

  public GpgModule(Config cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void configure() {
    boolean configEnableSignedPush = cfg.getBoolean("receive", null, "enableSignedPush", false);
    boolean configEditGpgKeys = cfg.getBoolean("gerrit", null, "editGpgKeys", true);
    boolean havePgp = BouncyCastleUtil.havePGP();
    boolean enableSignedPush = configEnableSignedPush && havePgp;
    bindConstant().annotatedWith(EnableSignedPush.class).to(enableSignedPush);

    if (configEnableSignedPush && !havePgp) {
      log.info("Bouncy Castle PGP not installed; signed push verification is" + " disabled");
    }
    if (enableSignedPush) {
      install(new SignedPushModule());
      factory(GerritPushCertificateChecker.Factory.class);
    }
    install(new GpgApiModule(enableSignedPush && configEditGpgKeys));
  }
}
