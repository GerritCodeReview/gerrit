// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitWholeTopicMode;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.ChangeSet;
import com.google.gerrit.server.submit.MergeOp;
import com.google.gerrit.server.submit.MergeSuperSet;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SubmitWholeTopic extends Submit {

  public final SubmitWholeTopicMode submitWholeTopicMode;

  @Inject
  SubmitWholeTopic(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      Provider<MergeOp> mergeOpProvider,
      Provider<MergeSuperSet> mergeSuperSet,
      AccountResolver accountResolver,
      @GerritServerConfig Config cfg,
      Provider<InternalChangeQuery> queryProvider,
      ChangeJson.Factory json,
      ChangeData.Factory changeDataFactory,
      ProjectCache projectCache,
      MergeUtilFactory mergeUtilFactory,
      MergeabilityCache mergeabilityCache) {
    super(
        repoManager,
        permissionBackend,
        mergeOpProvider,
        mergeSuperSet,
        accountResolver,
        cfg,
        true,
        queryProvider,
        json,
        changeDataFactory,
        projectCache,
        mergeUtilFactory,
        mergeabilityCache);
    submitWholeTopicMode = MergeSuperSet.wholeTopicMode(cfg);
  }

  /**
   * Rejects the REST call unless the mode is {@link SubmitWholeTopicMode#OPTIONAL}.
   *
   * <ul>
   *   <li>{@link SubmitWholeTopicMode#DISABLED} — feature is off; endpoint returns 405.
   *   <li>{@link SubmitWholeTopicMode#ENFORCED} — every normal submit already includes the whole
   *       topic; this dedicated endpoint is redundant and returns 405.
   *   <li>{@link SubmitWholeTopicMode#OPTIONAL} — user explicitly opts in; proceed normally.
   * </ul>
   */
  @Override
  public Response<ChangeInfo> apply(RevisionResource rsrc, @Nullable SubmitInput input)
      throws RestApiException,
          RepositoryNotFoundException,
          IOException,
          PermissionBackendException,
          UpdateException,
          ConfigInvalidException {
    if (submitWholeTopicMode != SubmitWholeTopicMode.OPTIONAL) {
      throw new MethodNotAllowedException("submit whole topic is not available");
    }
    return super.apply(rsrc, input);
  }

  @Nullable
  @Override
  public UiAction.Description getDescription(RevisionResource resource)
      throws IOException, PermissionBackendException {
    // Only OPTIONAL mode exposes the dedicated action. For DISABLED the feature is off entirely;
    // for ENFORCED every normal submit already submits the whole topic, so the action is redundant.
    if (submitWholeTopicMode != SubmitWholeTopicMode.OPTIONAL) {
      return null;
    }
    ChangeData cd = resource.getChangeResource().getChangeData();
    Change change = cd.change();

    if (!isActionVisible(resource, change, cd)) {
      return null;
    }

    ChangeSet cs = getChangeSet(resource, cd);
    String submitProblems = problemsForSubmittingChangeset(cd, cs, resource.getUser());

    String topic = change.getTopic();
    int topicSize = getTopicSize(topic);
    if (Strings.isNullOrEmpty(topic) || topicSize <= 1) {
      return null; // submit whole topic not visible
    }

    if (submitProblems != null) {
      return new UiAction.Description()
          .setLabel(submitTopicLabel)
          .setTitle(submitProblems)
          .setVisible(true)
          .setEnabled(false);
    }

    // Recheck mergeability rather than using value stored in the index, which may be stale.
    // TODO(dborowitz): This is ugly; consider providing a way to not read stored fields from the
    // index in the first place.
    // cd.setMergeable(null);
    // That was done in unmergeableChanges which was called by problemsForSubmittingChangeset, so
    // now it is safe to read from the cache, as it yields the same result.
    Boolean enabled = useMergeabilityCheck ? cd.isMergeable() : true;

    return getSubmitTopicDescription(topicSize, cs, enabled);
  }

  /**
   * Change-level REST API endpoint that calls submit whole topic for the latest revision on a
   * change.
   *
   * <p>See /Documentation/rest-api-changes.html#submit-change for more information.
   */
  public static class CurrentRevision extends Submit.CurrentRevision {
    @Inject
    CurrentRevision(SubmitWholeTopic submit, PatchSetUtil psUtil) {
      super(submit, psUtil);
    }
  }
}
