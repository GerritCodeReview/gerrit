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

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.util.common.CommonConverters;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class ChangeEditJson {
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final Provider<CurrentUser> userProvider;

  @Inject
  ChangeEditJson(
      DynamicMap<DownloadCommand> downloadCommand,
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
    out.basePatchSetNumber = edit.getBasePatchSet().getPatchSetId();
    if (downloadCommands) {
      out.fetch = fillFetchMap(edit);
    }
    return out;
  }

  private static CommitInfo fillCommit(RevCommit editCommit) {
    CommitInfo commit = new CommitInfo();
    commit.commit = editCommit.toObjectId().getName();
    commit.author = CommonConverters.toGitPerson(editCommit.getAuthorIdent());
    commit.committer = CommonConverters.toGitPerson(editCommit.getCommitterIdent());
    commit.subject = editCommit.getShortMessage();
    commit.message = editCommit.getFullMessage();

    commit.parents = new ArrayList<>(editCommit.getParentCount());
    for (RevCommit p : editCommit.getParents()) {
      CommitInfo i = new CommitInfo();
      i.commit = p.name();
      commit.parents.add(i);
    }

    return commit;
  }

  private Map<String, FetchInfo> fillFetchMap(ChangeEdit edit) {
    Map<String, FetchInfo> r = new LinkedHashMap<>();
    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      String schemeName = e.getExportName();
      DownloadScheme scheme = e.getProvider().get();
      if (!scheme.isEnabled()
          || (scheme.isAuthRequired() && !userProvider.get().isIdentifiedUser())) {
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

      ChangeJson.populateFetchMap(scheme, downloadCommands, projectName, refName, fetchInfo);
    }

    return r;
  }
}
