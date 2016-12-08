// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.query.InternalQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.List;
import java.util.Set;

public class InternalAccountQuery extends InternalQuery<AccountState> {
  @Inject
  InternalAccountQuery(AccountQueryProcessor queryProcessor,
      AccountIndexCollection indexes,
      IndexConfig indexConfig) {
    super(queryProcessor, indexes, indexConfig);
  }

  @Override
  public InternalAccountQuery setLimit(int n) {
    super.setLimit(n);
    return this;
  }

  @Override
  public InternalAccountQuery enforceVisibility(boolean enforce) {
    super.enforceVisibility(enforce);
    return this;
  }

  @Override
  public InternalAccountQuery setRequestedFields(Set<String> fields) {
    super.setRequestedFields(fields);
    return this;
  }

  @Override
  public InternalAccountQuery noFields() {
    super.noFields();
    return this;
  }

  public List<AccountState> byDefault(String query)
      throws OrmException {
    return query(AccountPredicates.defaultPredicate(query));
  }

  public List<AccountState> byExternalId(String externalId)
      throws OrmException {
    return query(AccountPredicates.externalId(externalId));
  }

  public List<AccountState> byFullName(String fullName)
      throws OrmException {
    return query(AccountPredicates.fullName(fullName));
  }

  public List<AccountState> byWatchedProject(Project.NameKey project)
      throws OrmException {
    return query(AccountPredicates.watchedProject(project));
  }
}
