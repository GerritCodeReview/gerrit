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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class GetRevisionActions implements ETagView<RevisionResource> {
  private final ActionJson delegate;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Config config;
  @Inject
  GetRevisionActions(
      ActionJson delegate,
      Provider<InternalChangeQuery> queryProvider,
      @GerritServerConfig Config config) {
    this.delegate = delegate;
    this.queryProvider = queryProvider;
    this.config = config;
  }

  @Override
  public Object apply(RevisionResource rsrc) {
    return Response.withMustRevalidate(delegate.format(rsrc));
  }

  @Override
  public String getETag(RevisionResource rsrc) {
    String topic = rsrc.getChange().getTopic();
    if (!Submit.wholeTopicEnabled(config)
        || Strings.isNullOrEmpty(topic)) {
      return rsrc.getETag();
    }
    Hasher h = Hashing.md5().newHasher();
    CurrentUser user = rsrc.getControl().getCurrentUser();
    try {
      for (ChangeData c : queryProvider.get().byTopicOpen(topic)) {
        new ChangeResource(c.changeControl()).prepareETag(h, user);
      }
    } catch (OrmException e){
      throw new OrmRuntimeException(e);
    }
    return h.hash().toString();
  }
}
