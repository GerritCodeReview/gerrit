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

package com.google.gerrit.httpd.rpc.account;

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.common.collect.Lists;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.List;

class ExternalIdDetailFactory extends Handler<List<AccountExternalId>> {
  private static final ProtobufCodec<AccountExternalId> codec =
      CodecFactory.encoder(AccountExternalId.class);

  interface Factory {
    ExternalIdDetailFactory create();
  }

  private final IdentifiedUser user;
  private final AuthConfig authConfig;
  private final WebSession session;

  @Inject
  ExternalIdDetailFactory(final IdentifiedUser user,
      final AuthConfig authConfig, final WebSession session) {
    this.user = user;
    this.authConfig = authConfig;
    this.session = session;
  }

  @Override
  public List<AccountExternalId> call() {
    AccountExternalId.Key last = session.getLastLoginExternalId();
    List<AccountExternalId> ids = load();

    for (final AccountExternalId e : ids) {
      e.setTrusted(authConfig.isIdentityTrustable(Collections.singleton(e)));

      // The identity can be deleted only if its not the one used to
      // establish this web session, and if only if an identity was
      // actually used to establish this web session.
      //
      if (e.isScheme(SCHEME_USERNAME)) {
        e.setCanDelete(false);
      } else {
        e.setCanDelete(last != null && !last.equals(e.getKey()));
      }
    }

    return ids;
  }

  private List<AccountExternalId> load() {
    List<AccountExternalId> res = Lists.newArrayList();
    for (AccountExternalId id : user.getAccountState().getExternalIds()) {
      res.add(clone(id));
    }
    return res;
  }

  private static AccountExternalId clone(AccountExternalId id) {
    return codec.decode(codec.encodeToByteArray(id));
  }
}
