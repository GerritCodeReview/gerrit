// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.gerrit.entities.RefNames.changeMetaRef;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInfoDiffer;
import com.google.gerrit.extensions.common.ChangeInfoDifference;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

/** Gets the diff for a change at two NoteDb meta SHA-1s. */
public class GetMetaDiff
    implements RestReadView<ChangeResource>,
        DynamicOptions.BeanReceiver,
        DynamicOptions.BeanProvider {

  private final EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
  private final Map<String, DynamicBean> dynamicBeans = new HashMap<>();

  private final Provider<GetChange> getChangeProvider;
  private final GitRepositoryManager repoManager;

  @Option(name = "-o", usage = "Output options")
  public void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) throws BadRequestException {
    options.addAll(ListOption.fromHexString(ListChangesOption.class, hex));
  }

  @Option(name = "--old", usage = "old NoteDb meta SHA-1")
  String oldMetaRevId = "";

  public void setOldMetaRevId(@Nullable String oldMetaRevId) {
    this.oldMetaRevId = oldMetaRevId == null ? "" : oldMetaRevId;
  }

  @Option(name = "--meta", usage = "new NoteDb meta SHA-1")
  String metaRevId = "";

  public void setNewMetaRevId(@Nullable String metaRevId) {
    this.metaRevId = metaRevId == null ? "" : metaRevId;
  }

  @Inject
  GetMetaDiff(Provider<GetChange> getChangeProvider, GitRepositoryManager repoManager) {
    this.getChangeProvider = getChangeProvider;
    this.repoManager = repoManager;
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
  public Response<ChangeInfoDifference> apply(ChangeResource resource)
      throws RestApiException, IOException {
    return Response.ok(
        ChangeInfoDiffer.getDifference(getOldChangeInfo(resource), getNewChangeInfo(resource)));
  }

  private ChangeInfo getOldChangeInfo(ChangeResource resource)
      throws RestApiException, IOException {
    GetChange getChange = createGetChange();
    getChange.setMetaRevId(getOldMetaRevId(resource));
    ChangeInfo oldChangeInfo;
    try {
      oldChangeInfo = getChange.apply(resource).value();
    } catch (PreconditionFailedException e) {
      oldChangeInfo = new ChangeInfo();
    }
    return oldChangeInfo;
  }

  private String getOldMetaRevId(ChangeResource resource)
      throws IOException, BadRequestException, PreconditionFailedException {
    if (!oldMetaRevId.isEmpty()) {
      return oldMetaRevId;
    }
    String newMetaRevId = getNewMetaRevId(resource);
    try (Repository repo = repoManager.openRepository(resource.getProject());
        RevWalk rw = new RevWalk(repo)) {
      ObjectId resourceId = ObjectId.fromString(newMetaRevId);
      RevCommit commit = rw.parseCommit(resourceId);
      return commit.getParentCount() == 0
          ? resourceId.getName()
          : commit.getParent(0).getId().getName();
    } catch (InvalidObjectIdException e) {
      throw new BadRequestException("invalid meta SHA1: " + newMetaRevId, e);
    } catch (MissingObjectException e) {
      throw new PreconditionFailedException(e.getMessage());
    }
  }

  private ChangeInfo getNewChangeInfo(ChangeResource resource)
      throws RestApiException, IOException {
    GetChange getChange = createGetChange();
    getChange.setMetaRevId(getNewMetaRevId(resource));
    return getChange.apply(resource).value();
  }

  private String getNewMetaRevId(ChangeResource resource) throws IOException {
    if (!metaRevId.isEmpty()) {
      return metaRevId;
    }
    try (Repository repo = repoManager.openRepository(resource.getProject())) {
      return repo.exactRef(changeMetaRef(resource.getId())).getObjectId().getName();
    }
  }

  private GetChange createGetChange() {
    GetChange getChange = getChangeProvider.get();
    options.forEach(getChange::addOption);
    dynamicBeans.forEach(getChange::setDynamicBean);
    return getChange;
  }
}
