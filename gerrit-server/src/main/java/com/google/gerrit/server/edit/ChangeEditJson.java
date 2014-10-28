// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.webui.PrivateInternals_UiActionDescription;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Map;

@Singleton
public class ChangeEditJson {
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final Provider<CurrentUser> userProvider;

  @Inject
  ChangeEditJson(DynamicMap<DownloadCommand> downloadCommand,
      DynamicMap<DownloadScheme> downloadSchemes,
      Provider<CurrentUser> userProvider) {
    this.downloadCommands = downloadCommand;
    this.downloadSchemes = downloadSchemes;
    this.userProvider = userProvider;
  }

  public EditInfo toEditInfo(ChangeEdit edit, boolean downloadCommands) {
    EditInfo out = new EditInfo();
    out.commit = fillCommit(edit.getEditCommit());
    out.baseRevision = edit.getBasePatchSet().getRevision().get();
    out.actions = fillActions(edit);
    if (downloadCommands) {
      out.fetch = fillFetchMap(edit);
    }
    return out;
  }

  private static CommitInfo fillCommit(RevCommit editCommit) {
    CommitInfo commit = new CommitInfo();
    commit.commit = editCommit.toObjectId().getName();
    commit.parents = Lists.newArrayListWithCapacity(1);
    commit.author = CommonConverters.toGitPerson(editCommit.getAuthorIdent());
    commit.committer = CommonConverters.toGitPerson(
        editCommit.getCommitterIdent());
    commit.subject = editCommit.getShortMessage();
    commit.message = editCommit.getFullMessage();

    CommitInfo i = new CommitInfo();
    i.commit = editCommit.getParent(0).toObjectId().getName();
    commit.parents.add(i);

    return commit;
  }

  private static Map<String, ActionInfo> fillActions(ChangeEdit edit) {
    Map<String, ActionInfo> actions = Maps.newTreeMap();

    UiAction.Description descr = new UiAction.Description();
    PrivateInternals_UiActionDescription.setId(descr, "/");
    PrivateInternals_UiActionDescription.setMethod(descr, "DELETE");
    descr.setTitle("Delete edit");
    actions.put(descr.getId(), new ActionInfo(descr));

    // Only expose publish action when the edit is on top of current ps
    PatchSet.Id current = edit.getChange().currentPatchSetId();
    PatchSet basePs = edit.getBasePatchSet();
    if (basePs.getId().equals(current)) {
      descr = new UiAction.Description();
      PrivateInternals_UiActionDescription.setId(descr, "publish");
      PrivateInternals_UiActionDescription.setMethod(descr, "POST");
      descr.setTitle("Publish edit");
      actions.put(descr.getId(), new ActionInfo(descr));
    } else {
      descr = new UiAction.Description();
      PrivateInternals_UiActionDescription.setId(descr, "rebase");
      PrivateInternals_UiActionDescription.setMethod(descr, "POST");
      descr.setTitle("Rebase edit");
      actions.put(descr.getId(), new ActionInfo(descr));
    }

    return actions;
  }

  private Map<String, FetchInfo> fillFetchMap(ChangeEdit edit) {
    Map<String, FetchInfo> r = Maps.newLinkedHashMap();
    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      String schemeName = e.getExportName();
      DownloadScheme scheme = e.getProvider().get();
      if (!scheme.isEnabled()
          || (scheme.isAuthRequired()
              && !userProvider.get().isIdentifiedUser())) {
        continue;
      }

      // No fluff, just stuff
      if (!scheme.isAuthSupported()) {
        continue;
      }

      String projectName = edit.getChange().getProject().get();
      String refName = edit.getRefName();
      FetchInfo fetchInfo = new FetchInfo(scheme.getUrl(projectName), refName);
      r.put(schemeName, fetchInfo);

      ChangeJson.populateFetchMap(scheme, downloadCommands, projectName,
          refName, fetchInfo);
    }

    return r;
  }
}
