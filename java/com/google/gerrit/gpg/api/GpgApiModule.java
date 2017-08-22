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

package com.google.gerrit.gpg.api;

import static com.google.gerrit.gpg.server.GpgKey.GPG_KEY_KIND;
import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;

import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.PushCertificateInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.gpg.server.DeleteGpgKey;
import com.google.gerrit.gpg.server.GpgKeys;
import com.google.gerrit.gpg.server.PostGpgKeys;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.api.accounts.GpgApiAdapter;
import java.util.List;
import java.util.Map;

public class GpgApiModule extends RestApiModule {
  private final boolean enabled;

  public GpgApiModule(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  protected void configure() {
    if (!enabled) {
      bind(GpgApiAdapter.class).to(NoGpgApi.class);
      return;
    }
    bind(GpgApiAdapter.class).to(GpgApiAdapterImpl.class);
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
    public boolean isEnabled() {
      return false;
    }

    @Override
    public Map<String, GpgKeyInfo> listGpgKeys(AccountResource account) {
      throw new NotImplementedException(MSG);
    }

    @Override
    public Map<String, GpgKeyInfo> putGpgKeys(
        AccountResource account, List<String> add, List<String> delete) {
      throw new NotImplementedException(MSG);
    }

    @Override
    public GpgKeyApi gpgKey(AccountResource account, IdString idStr) {
      throw new NotImplementedException(MSG);
    }

    @Override
    public PushCertificateInfo checkPushCertificate(String certStr, IdentifiedUser expectedUser) {
      throw new NotImplementedException(MSG);
    }
  }
}
