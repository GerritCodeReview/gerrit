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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.ContactInformationStoreException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.spearce.jgit.lib.Config;

import java.io.File;

public class EncryptedContactStoreProvider implements Provider<ContactStore> {
  private final Config config;
  private final SchemaFactory<ReviewDb> schema;
  private final File sitePath;

  @Inject
  EncryptedContactStoreProvider(@GerritServerConfig final Config config,
      final SchemaFactory<ReviewDb> schema, @SitePath final File sitePath) {
    this.config = config;
    this.schema = schema;
    this.sitePath = sitePath;
  }

  @Override
  public ContactStore get() {
    try {
      return new EncryptedContactStore(config, sitePath, schema);
    } catch (final ContactInformationStoreException initError) {
      return new ContactStore() {
        @Override
        public boolean isEnabled() {
          return false;
        }

        @Override
        public void store(Account account, ContactInformation info)
            throws ContactInformationStoreException {
          throw initError;
        }
      };
    }
  }
}
