// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.api.changes;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.api.changes.ChangeApiImpl.DynamicOptionParser;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.change.CreateChange;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
class ChangesImpl implements Changes {
  private final ChangesCollection changes;
  private final ChangeApiImpl.Factory api;
  private final CreateChange createChange;
  private final DynamicOptionParser dynamicOptionParser;
  private final Provider<QueryChanges> queryProvider;

  @Inject
  ChangesImpl(
      ChangesCollection changes,
      ChangeApiImpl.Factory api,
      CreateChange createChange,
      DynamicOptionParser dynamicOptionParser,
      Provider<QueryChanges> queryProvider) {
    this.changes = changes;
    this.api = api;
    this.createChange = createChange;
    this.dynamicOptionParser = dynamicOptionParser;
    this.queryProvider = queryProvider;
  }

  @Override
  public ChangeApi id(int id) throws RestApiException {
    return id(String.valueOf(id));
  }

  @Override
  public ChangeApi id(String project, String branch, String id) throws RestApiException {
    return id(
        Joiner.on('~')
            .join(ImmutableList.of(Url.encode(project), Url.encode(branch), Url.encode(id))));
  }

  @Override
  public ChangeApi id(String id) throws RestApiException {
    try {
      return api.create(changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse change", e);
    }
  }

  @Override
  public ChangeApi id(String project, int id) throws RestApiException {
    return id(
        Joiner.on('~').join(ImmutableList.of(Url.encode(project), Url.encode(String.valueOf(id)))));
  }

  @Override
  public ChangeApi create(ChangeInput in) throws RestApiException {
    try {
      ChangeInfo out = createChange.apply(TopLevelResource.INSTANCE, in).value();
      return api.create(changes.parse(Change.id(out._number)));
    } catch (Exception e) {
      throw asRestApiException("Cannot create change", e);
    }
  }

  @Override
  public QueryRequest query() {
    return new QueryRequest() {
      @Override
      public List<ChangeInfo> get() throws RestApiException {
        return ChangesImpl.this.get(this);
      }
    };
  }

  @Override
  public QueryRequest query(String query) {
    return query().withQuery(query);
  }

  private List<ChangeInfo> get(QueryRequest q) throws RestApiException {
    QueryChanges qc = queryProvider.get();
    if (q.getQuery() != null) {
      qc.addQuery(q.getQuery());
    }
    qc.setLimit(q.getLimit());
    qc.setStart(q.getStart());
    qc.setNoLimit(q.getNoLimit());
    for (ListChangesOption option : q.getOptions()) {
      qc.addOption(option);
    }
    dynamicOptionParser.parseDynamicOptions(qc, q.getPluginOptions());

    try {
      List<?> result = qc.apply(TopLevelResource.INSTANCE).value();
      if (result.isEmpty()) {
        return ImmutableList.of();
      }

      // Check type safety of result; the extension API should be safer than the
      // REST API in this case, since it's intended to be used in Java.
      Object first = requireNonNull(result.iterator().next());
      checkState(first instanceof ChangeInfo);
      @SuppressWarnings("unchecked")
      List<ChangeInfo> infos = (List<ChangeInfo>) result;

      return ImmutableList.copyOf(infos);
    } catch (Exception e) {
      throw asRestApiException("Cannot query changes", e);
    }
  }
}
