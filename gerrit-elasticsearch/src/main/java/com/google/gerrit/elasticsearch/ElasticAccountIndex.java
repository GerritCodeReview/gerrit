/*
 * Copyright 2016 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.elasticsearch;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;

import java.io.IOException;

class ElasticAccountIndex implements AccountIndex, LifecycleListener {

  @Inject
  ElasticAccountIndex() {
  }

  @Override
  public Schema<AccountState> getSchema() {
    return null;
  }

  @Override
  public void close() {
  }

  @Override
  public void replace(AccountState obj) throws IOException {
  }

  @Override
  public void delete(Id key) throws IOException {
  }

  @Override
  public void deleteAll() throws IOException {
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p,
      QueryOptions opts) throws QueryParseException {
    return null;
  }

  @Override
  public void markReady(boolean ready) throws IOException {
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }
}
