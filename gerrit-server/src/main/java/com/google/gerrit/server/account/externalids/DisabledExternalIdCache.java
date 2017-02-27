// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

public class DisabledExternalIdCache implements ExternalIdCache {
  public static Module module() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        bind(ExternalIdCache.class).to(DisabledExternalIdCache.class);
      }
    };
  }

  @Override
  public void onCreate(ObjectId newNotesRev, Iterable<ExternalId> extId) {}

  @Override
  public void onUpdate(ObjectId newNotesRev, Iterable<ExternalId> extId) {}

  @Override
  public void onReplace(
      ObjectId newNotesRev,
      Account.Id accountId,
      Iterable<ExternalId> toRemove,
      Iterable<ExternalId> toAdd) {}

  @Override
  public void onReplaceByKeys(
      ObjectId newNotesRev,
      Account.Id accountId,
      Iterable<ExternalId.Key> toRemove,
      Iterable<ExternalId> toAdd) {}

  @Override
  public void onReplace(
      ObjectId newNotesRev, Iterable<ExternalId> toRemove, Iterable<ExternalId> toAdd) {}

  @Override
  public void onRemove(ObjectId newNotesRev, Iterable<ExternalId> extId) {}

  @Override
  public void onRemove(
      ObjectId newNotesRev, Account.Id accountId, Iterable<ExternalId.Key> extIdKeys) {}

  @Override
  public Set<ExternalId> byAccount(Account.Id accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<ExternalId> byEmail(String email) throws IOException {
    throw new UnsupportedOperationException();
  }
}
