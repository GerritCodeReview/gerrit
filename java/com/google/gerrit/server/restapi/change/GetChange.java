// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.EnumSet;
import java.util.List;
import org.kohsuke.args4j.Option;

public class GetChange implements ETagView<ChangeResource> {
  private final ChangeJson.Factory json;
  private final EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
  private final Provider<InternalChangeQuery> queryProvider;

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListChangesOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Inject
  GetChange(ChangeJson.Factory json, Provider<InternalChangeQuery> queryProvider) {
    this.json = json;
    this.queryProvider = queryProvider;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws OrmException {
    return Response.withMustRevalidate(json.create(options).format(rsrc));
  }

  Response<ChangeInfo> apply(RevisionResource rsrc) throws OrmException {
    return Response.withMustRevalidate(json.create(options).format(rsrc));
  }

  @Override
  public String getETag(ChangeResource rsrc) {
    Hasher h = Hashing.murmur3_128().newHasher();
    CurrentUser user = rsrc.getUser();
    try {
      rsrc.prepareETag(h, user);
      // check if change data are not re-indexed
      List<ChangeData> cds = queryProvider.get().byLegacyChangeId(rsrc.getChange().getId());
      checkState(cds.size() <= 1, "Expected one or zero ChangeData, got " + cds.size());
      ChangeData cd = Iterables.getFirst(cds, null);
      if (cd != null) {
        h.putLong(cd.change().getLastUpdatedOn().getTime());
        h.putInt(cd.change().getRowVersion());
      }
    } catch (OrmException e) {
      throw new OrmRuntimeException(e);
    }
    return h.hash().toString();
  }
}
