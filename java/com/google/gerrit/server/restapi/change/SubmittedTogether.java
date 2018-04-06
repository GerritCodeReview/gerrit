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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.api.changes.SubmittedTogetherOption.NON_VISIBLE_CHANGES;

import com.google.gerrit.extensions.api.changes.SubmittedTogetherInfo;
import com.google.gerrit.extensions.api.changes.SubmittedTogetherOption;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubmittedTogether implements RestReadView<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(SubmittedTogether.class);

  private static final Comparator<ChangeData> COMPARATOR =
      Comparator.comparing(ChangeData::project).thenComparing(cd -> cd.getId().id).reversed();

  private final EnumSet<SubmittedTogetherOption> options =
      EnumSet.noneOf(SubmittedTogetherOption.class);

  private final EnumSet<ListChangesOption> jsonOpt =
      EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.SUBMITTABLE);

  private final ChangeJson.Factory json;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<MergeSuperSet> mergeSuperSet;

  private boolean lazyLoad = false;

  @Option(name = "-o", usage = "Output options")
  void addOption(String option) {
    for (ListChangesOption o : ListChangesOption.values()) {
      if (o.name().equalsIgnoreCase(option)) {
        jsonOpt.add(o);
        lazyLoad |= ChangeJson.REQUIRE_LAZY_LOAD.contains(o);
        return;
      }
    }

    for (SubmittedTogetherOption o : SubmittedTogetherOption.values()) {
      if (o.name().equalsIgnoreCase(option)) {
        options.add(o);
        return;
      }
    }

    throw new IllegalArgumentException("option not recognized: " + option);
  }

  @Inject
  SubmittedTogether(
      ChangeJson.Factory json,
      Provider<ReviewDb> dbProvider,
      Provider<InternalChangeQuery> queryProvider,
      Provider<MergeSuperSet> mergeSuperSet) {
    this.json = json;
    this.dbProvider = dbProvider;
    this.queryProvider = queryProvider;
    this.mergeSuperSet = mergeSuperSet;
  }

  public SubmittedTogether addListChangesOption(EnumSet<ListChangesOption> o) {
    jsonOpt.addAll(o);
    return this;
  }

  public SubmittedTogether addSubmittedTogetherOption(EnumSet<SubmittedTogetherOption> o) {
    options.addAll(o);
    return this;
  }

  @Override
  public Object apply(ChangeResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, IOException,
          OrmException, PermissionBackendException {
    SubmittedTogetherInfo info = applyInfo(resource);
    if (options.isEmpty()) {
      return info.changes;
    }
    return info;
  }

  public SubmittedTogetherInfo applyInfo(ChangeResource resource)
      throws AuthException, IOException, OrmException, PermissionBackendException {
    Change c = resource.getChange();
    try {
      List<ChangeData> cds;
      int hidden;

      if (c.getStatus().isOpen()) {
        ChangeSet cs =
            mergeSuperSet.get().completeChangeSet(dbProvider.get(), c, resource.getUser());
        cds = cs.changes().asList();
        hidden = cs.nonVisibleChanges().size();
      } else if (c.getStatus().asChangeStatus() == ChangeStatus.MERGED) {
        cds = queryProvider.get().bySubmissionId(c.getSubmissionId());
        hidden = 0;
      } else {
        cds = Collections.emptyList();
        hidden = 0;
      }

      if (hidden != 0 && !options.contains(NON_VISIBLE_CHANGES)) {
        throw new AuthException("change would be submitted with a change that you cannot see");
      }

      if (cds.size() <= 1 && hidden == 0) {
        cds = Collections.emptyList();
      } else {
        cds = new ArrayList<>(cds);
        Collections.sort(cds, COMPARATOR);
      }

      SubmittedTogetherInfo info = new SubmittedTogetherInfo();
      info.changes = json.create(jsonOpt).lazyLoad(lazyLoad).formatChangeDatas(cds);
      info.nonVisibleChanges = hidden;
      return info;
    } catch (OrmException | IOException e) {
      log.error("Error on getting a ChangeSet", e);
      throw e;
    }
  }
}
