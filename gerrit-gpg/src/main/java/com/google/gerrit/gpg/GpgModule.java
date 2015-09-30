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

import static com.google.gerrit.gpg.server.GpgKey.GPG_KEY_KIND;
import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;

import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.gpg.api.GpgApiAdapterImpl;
import com.google.gerrit.gpg.api.GpgKeyApiImpl;
import com.google.gerrit.gpg.server.DeleteGpgKey;
import com.google.gerrit.gpg.server.GpgKeys;
import com.google.gerrit.gpg.server.PostGpgKeys;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.api.accounts.GpgApiAdapter;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class GpgModule extends RestApiModule {
  private static final Logger log = LoggerFactory.getLogger(GpgModule.class);

  private final Config cfg;

  public GpgModule(Config cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void configure() {
    boolean configEnableSignedPush =
        cfg.getBoolean("receive", null, "enableSignedPush", false);
    boolean havePgp = BouncyCastleUtil.havePGP();
    boolean enableSignedPush = configEnableSignedPush && havePgp;
    bindConstant().annotatedWith(EnableSignedPush.class).to(enableSignedPush);

    if (configEnableSignedPush && !havePgp) {
      log.info("Bouncy Castle PGP not installed; signed push verification is"
          + " disabled");
    }
    if (!enableSignedPush) {
      bind(GpgApiAdapter.class).to(NoGpgApi.class);
      return;
    }

    install(new SignedPushModule());
    bind(GpgApiAdapter.class).to(GpgApiAdapterImpl.class);
    factory(GerritPushCertificateChecker.Factory.class);
    factory(GpgKeyApiImpl.Factory.class);

    DynamicMap.mapOf(binder(), GPG_KEY_KIND);

    child(ACCOUNT_KIND, "gpgkeys").to(GpgKeys.class);
    post(ACCOUNT_KIND, "gpgkeys").to(PostGpgKeys.class);
    get(GPG_KEY_KIND).to(GpgKeys.Get.class);
    delete(GPG_KEY_KIND).to(DeleteGpgKey.class);
  }

  private static class NoGpgApi implements GpgApiAdapter {
    private static final String MSG = "GPG key APIs disabled";

    @Override
    public Map<String, GpgKeyInfo> listGpgKeys(AccountResource account) {
      throw new NotImplementedException(MSG);
    }

    @Override
    public Map<String, GpgKeyInfo> putGpgKeys(AccountResource account,
        List<String> add, List<String> delete) {
      throw new NotImplementedException(MSG);
    }

    @Override
    public GpgKeyApi gpgKey(AccountResource account, IdString idStr) {
      throw new NotImplementedException(MSG);
    }
  }
}
