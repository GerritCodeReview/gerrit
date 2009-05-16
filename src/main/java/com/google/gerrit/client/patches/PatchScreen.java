// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NoDifferencesException;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtjsonrpc.client.RemoteJsonException;

public abstract class PatchScreen extends Screen {
  protected final Patch.Key patchKey;
  protected PatchSet.Id idSideA;
  protected PatchSet.Id idSideB;
  protected int contextLines;

  private DisclosurePanel historyPanel;
  private HistoryTable historyTable;
  private Label noDifference;
  private AbstractPatchContentTable patchTable;

  private int rpcSequence;
  private PatchScript script;
  private CommentDetail comments;

  protected PatchScreen(final Patch.Key id) {
    patchKey = id;
    idSideA = null;
    idSideB = id.getParentKey();

    if (Gerrit.isSignedIn()) {
      final AccountGeneralPreferences p =
          Gerrit.getUserAccount().getGeneralPreferences();
      contextLines = p.getDefaultContext();
    } else {
      contextLines = AccountGeneralPreferences.DEFAULT_CONTEXT;
    }
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    final Change.Id changeId = patchKey.getParentKey().getParentKey();
    final String path = patchKey.get();
    String fileName = path;
    final int last = fileName.lastIndexOf('/');
    if (last >= 0) {
      fileName = fileName.substring(last + 1);
    }

    setWindowTitle(PatchUtil.M.patchWindowTitle(changeId.get(), fileName));
    setPageTitle(PatchUtil.M.patchPageTitle(changeId.get(), path));

    historyTable = new HistoryTable(this);
    historyPanel = new DisclosurePanel(PatchUtil.C.patchHistoryTitle());
    historyPanel.setContent(historyTable);
    historyPanel.setOpen(false);
    historyPanel.setVisible(false);
    add(historyPanel);

    noDifference = new Label(PatchUtil.C.noDifference());
    noDifference.setStyleName("gerrit-PatchNoDifference");
    noDifference.setVisible(false);
    patchTable = createPatchTable();

    final FlowPanel fp = new FlowPanel();
    fp.setStyleName("gerrit-SideBySideScreen-SideBySideTable");
    fp.add(noDifference);
    fp.add(patchTable);
    add(fp);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    refresh(true);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    patchTable.setRegisterKeys(patchTable.isVisible());
  }

  protected abstract AbstractPatchContentTable createPatchTable();

  protected void refresh(final boolean isFirst) {
    final int rpcseq = ++rpcSequence;
    script = null;
    comments = null;

    PatchUtil.DETAIL_SVC.patchScript(patchKey, idSideA, idSideB, contextLines,
        new GerritCallback<PatchScript>() {
          public void onSuccess(final PatchScript result) {
            if (rpcSequence == rpcseq) {
              script = result;
              onResult();
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            if (rpcSequence == rpcseq) {
              if (isNoDifferences(caught) && !isFirst) {
                historyTable.enableAll(true);
                showPatch(false);
              } else {
                super.onFailure(caught);
              }
            }
          }

          private boolean isNoDifferences(final Throwable caught) {
            if (caught instanceof NoDifferencesException) {
              return true;
            }
            return caught instanceof RemoteJsonException
                && caught.getMessage().equals(NoDifferencesException.MESSAGE);
          }
        });

    PatchUtil.DETAIL_SVC.patchComments(patchKey, idSideA, idSideB,
        new GerritCallback<CommentDetail>() {
          public void onSuccess(final CommentDetail result) {
            if (rpcSequence == rpcseq) {
              comments = result;
              onResult();
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            // Ignore no such entity, the patch script RPC above would
            // also notice the problem and report it.
            //
            if (!isNoSuchEntity(caught) && rpcSequence == rpcseq) {
              super.onFailure(caught);
            }
          }
        });
  }

  private void onResult() {
    if (script != null && comments != null) {
      if (comments.getHistory().size() > 1) {
        historyTable.display(comments.getHistory());
        historyPanel.setVisible(true);
      } else {
        historyPanel.setVisible(false);
      }

      patchTable.display(patchKey, idSideA, idSideB, script);
      patchTable.display(comments);
      patchTable.finishDisplay();
      showPatch(true);

      script = null;
      comments = null;

      display();
    }
  }

  private void showPatch(final boolean showPatch) {
    noDifference.setVisible(!showPatch);
    patchTable.setVisible(showPatch);
    patchTable.setRegisterKeys(isCurrentView() && showPatch);
  }
}
