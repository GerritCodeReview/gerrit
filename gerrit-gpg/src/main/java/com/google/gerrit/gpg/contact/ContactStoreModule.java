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

package com.google.gerrit.gpg.contact;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.gpg.BouncyCastleUtil;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ContactStore;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates the {@link ContactStore} based on the configuration. */
public class ContactStoreModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Nullable
  @Provides
  public ContactStore provideContactStore(@GerritServerConfig Config config,
      SitePaths site, SchemaFactory<ReviewDb> schema,
      ContactStoreConnection.Factory connFactory) {
    String url = config.getString("contactstore", null, "url");
    if (StringUtils.isEmptyOrNull(url)) {
      return new NoContactStore();
    }

    if (!BouncyCastleUtil.havePGP()) {
      throw new ProvisionException("BouncyCastle PGP not installed; "
          + " needed to encrypt contact information");
    }

    URL storeUrl;
    try {
      storeUrl = new URL(url);
    } catch (MalformedURLException e) {
      throw new ProvisionException("Invalid contactstore.url: " + url, e);
    }

    String storeAPPSEC = config.getString("contactstore", null, "appsec");
    Path pubkey = site.contact_information_pub;
    if (!Files.exists(pubkey)) {
      throw new ProvisionException("PGP public key file \""
          + pubkey.toAbsolutePath() + "\" not found");
    }
    return new EncryptedContactStore(storeUrl, storeAPPSEC, pubkey, schema,
        connFactory);
  }
}
