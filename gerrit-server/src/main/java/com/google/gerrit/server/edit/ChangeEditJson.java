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
import com.google.gerrit.extensions.webui.PrivateInternals_UiActionDescription;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CommonConverters;
import com.google.inject.Singleton;

import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Map;

@Singleton
public class ChangeEditJson {

  public EditInfo toEditInfo(ChangeEdit edit) throws IOException {
    EditInfo out = new EditInfo();
    out.commit = fillCommit(edit.getEditCommit());
    out.actions = fillActions(edit);
    return out;
  }

  private static CommitInfo fillCommit(RevCommit editCommit) throws IOException {
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

    return actions;
  }
}
