/*
 * Copyright 2012 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.realm.guice;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.cache.RealmCacheModule;
import com.google.inject.Provider;

@Singleton
class RealmCacheModuleProvider implements Provider<RealmCacheModule> {

  private final RealmProvider realmProvider;

  @Inject
  RealmCacheModuleProvider(RealmProvider realmProvider) {
    this.realmProvider = realmProvider;
  }

  @Override
  public RealmCacheModule get() {
    return realmProvider.getCacheModule();
  }

}
