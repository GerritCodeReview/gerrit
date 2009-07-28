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

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.git.ChangeMergeQueue;
import com.google.gerrit.git.MergeQueue;
import com.google.gerrit.git.PushReplication;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.AbstractModule;
import static com.google.inject.Scopes.SINGLETON;

/** Starts {@link GerritServer} with standard dependencies. */
public class GerritServerModule extends AbstractModule {
  @Override
  protected void configure() {
    try {
      bind(GerritServer.class).toInstance(GerritServer.getInstance());
    } catch (OrmException e) {
      addError(e);
    } catch (XsrfException e) {
      addError(e);
    }

    bind(ContactStore.class).toProvider(EncryptedContactStoreProvider.class);
    bind(FileTypeRegistry.class).to(MimeUtilFileTypeRegistry.class);
    bind(ReplicationQueue.class).to(PushReplication.class).in(SINGLETON);
    bind(MergeQueue.class).to(ChangeMergeQueue.class).in(SINGLETON);
    bind(GerritConfig.class).toProvider(GerritConfigProvider.class).in(
        SINGLETON);
  }
}
