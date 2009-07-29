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
import com.google.gerrit.server.config.SitePath;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.File;

class EncryptedContactStoreProvider implements Provider<ContactStore> {
  @Inject
  private GerritServer server;

  @Inject
  private SchemaFactory<ReviewDb> schema;

  @Inject
  @SitePath
  private File sitePath;

  @Override
  public ContactStore get() {
    try {
      return new EncryptedContactStore(server, sitePath, schema);
    } catch (final ContactInformationStoreException initError) {
      return new ContactStore() {
        @Override
        public void store(Account account, ContactInformation info)
            throws ContactInformationStoreException {
          throw initError;
        }
      };
    }
  }
}
