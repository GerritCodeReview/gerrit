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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PluginDefinedAttributesFactories;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.MissingMetaObjectException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetChange
    implements RestReadView<ChangeResource>,
        DynamicOptions.BeanReceiver,
        DynamicOptions.BeanProvider {
  private final ChangeJson.Factory json;
  private final DynamicSet<ChangePluginDefinedInfoFactory> pdiFactories;
  private final EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
  private final Map<String, DynamicBean> dynamicBeans = new HashMap<>();
  private final GitRepositoryManager repoMgr;

  @Option(name = "-o", usage = "Output options")
  public void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "--meta", usage = "NoteDb meta SHA1")
  String metaRevId = "";

  public void setMetaRevId(String metaRevId) {
    this.metaRevId = metaRevId == null ? "" : metaRevId;
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) throws BadRequestException {
    EnumSet<ListChangesOption> optionSet = ListOption.fromHexString(ListChangesOption.class, hex);
    options.addAll(optionSet);
  }

  @Inject
  GetChange(
      ChangeJson.Factory json,
      DynamicSet<ChangePluginDefinedInfoFactory> pdiFactories,
      GitRepositoryManager repoMgr) {
    this.json = json;
    this.pdiFactories = pdiFactories;
    this.repoMgr = repoMgr;
  }

  @Override
  public void setDynamicBean(String plugin, DynamicOptions.DynamicBean dynamicBean) {
    dynamicBeans.put(plugin, dynamicBean);
  }

  @Override
  public DynamicBean getDynamicBean(String plugin) {
    return dynamicBeans.get(plugin);
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws RestApiException {
    try {
      Change change = rsrc.getChange();
      ObjectId changeMetaRevId = getMetaRevId(change);
      return Response.withMustRevalidate(newChangeJson().format(change, changeMetaRevId));
    } catch (MissingMetaObjectException e) {
      throw new PreconditionFailedException(e.getMessage());
    }
  }

  Response<ChangeInfo> apply(RevisionResource rsrc) {
    return Response.withMustRevalidate(newChangeJson().format(rsrc));
  }

  @Nullable
  private ObjectId getMetaRevId(Change change) throws RestApiException {
    if (metaRevId.isEmpty()) {
      return null;
    }

    // It might be interesting to also allow {SHA1}^^, so callers can walk back into history
    // without having to fetch the entire /meta ref. If we do so, we have to be careful that
    // the error messages can't be abused to fetch hidden data.
    ObjectId metaRevObjectId;
    try {
      metaRevObjectId = ObjectId.fromString(metaRevId);
    } catch (InvalidObjectIdException e) {
      throw new BadRequestException("invalid meta SHA1: " + metaRevId, e);
    }
    return verifyMetaId(change, metaRevObjectId);
  }

  private ChangeJson newChangeJson() {
    return json.create(options, this::createPluginDefinedInfos);
  }

  private ImmutableListMultimap<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
      Collection<ChangeData> cds) {
    return PluginDefinedAttributesFactories.createAll(
        cds, this, Streams.stream(pdiFactories.entries()));
  }

  @Nullable
  private ObjectId verifyMetaId(Change change, @Nullable ObjectId id) throws RestApiException {
    if (id == null) {
      return null;
    }

    String changeMetaRefName = RefNames.changeMetaRef(change.getId());
    try (Repository repo = repoMgr.openRepository(change.getProject());
        RevWalk rw = new RevWalk(repo)) {
      rw.setRetainBody(false);
      Ref ref = repo.getRefDatabase().exactRef(changeMetaRefName);
      RevCommit tip = rw.parseCommit(ref.getObjectId());
      rw.markStart(tip);
      for (RevCommit rev : rw) {
        if (id.equals(rev)) {
          return id;
        }
      }
    } catch (IOException e) {
      throw RestApiException.wrap(
          "I/O error while reading meta-ref id="
              + id.getName()
              + " from change "
              + change.getChangeId(),
          e);
    }

    throw new PreconditionFailedException(
        id.getName() + " not reachable from " + changeMetaRefName);
  }
}
