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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.EnumSet;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class GetChange implements ETagView<ChangeResource> {
  private final ChangeJson.Factory json;
  private final EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
  private final Config config;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<MergeSuperSet> mergeSuperSet;
  private final ChangeResource.Factory changeResourceFactory;

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListChangesOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Inject
  GetChange(
      ChangeJson.Factory json,
      Config config,
      Provider<ReviewDb> dbProvider,
      Provider<MergeSuperSet> mergeSuperSet,
      ChangeResource.Factory changeResourceFactory) {
    this.json = json;
    this.config = config;
    this.dbProvider = dbProvider;
    this.mergeSuperSet = mergeSuperSet;
    this.changeResourceFactory = changeResourceFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws OrmException {
    return Response.withMustRevalidate(json.create(options).format(rsrc));
  }

  Response<ChangeInfo> apply(RevisionResource rsrc) throws OrmException {
    return Response.withMustRevalidate(json.create(options).format(rsrc));
  }

  private static final SubmitRuleOptions SUBMIT_RULE_OPTIONS = SubmitRuleOptions.builder().build();

  @Override
  public String getETag(ChangeResource rsrc) {
    Hasher h = Hashing.murmur3_128().newHasher();
    CurrentUser user = rsrc.getUser();
    try {
      rsrc.prepareETag(h, user);
      h.putBoolean(MergeSuperSet.wholeTopicEnabled(config));
      ReviewDb db = dbProvider.get();
      ChangeSet cs = mergeSuperSet.get().completeChangeSet(db, rsrc.getChange(), user);
      Boolean allSubmitRulesOk = true;
      Boolean allMeargable = true;
      for (ChangeData cd : cs.changes()) {
        changeResourceFactory.create(cd.notes(), user).prepareETag(h, user);
        if (allSubmitRulesOk)
          allSubmitRulesOk = SubmitRecord.allRecordsOK(cd.getSubmitRecords(SUBMIT_RULE_OPTIONS));
        if (allMeargable) allMeargable = Boolean.TRUE.equals(cd.isMergeable());
      }

      h.putBoolean(allSubmitRulesOk);
      h.putBoolean(allMeargable);

    } catch (IOException | OrmException | PermissionBackendException e) {
      throw new OrmRuntimeException(e);
    }
    return h.hash().toString();
  }
}
