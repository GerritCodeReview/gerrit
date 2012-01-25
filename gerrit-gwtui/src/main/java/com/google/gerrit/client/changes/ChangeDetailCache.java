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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.ui.ListenableValue;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.client.Change;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FocusWidget;

public class ChangeDetailCache extends ListenableValue<ChangeDetail> {
  public static class GerritCallback extends
      com.google.gerrit.client.rpc.GerritCallback<ChangeDetail> {
    @Override
    public void onSuccess(ChangeDetail detail) {
      setChangeDetail(detail);
    }
  }

  /*
   * GerritCallback which will re-enable a FocusWidget
   * {@link com.google.gwt.user.client.ui.FocusWidget} if we are returning
   * with a failed result.
   *
   * It is up to the caller to handle the original disabling of the Widget.
   */
  public static class GerritWidgetCallback extends GerritCallback {
    private FocusWidget widget;

    public GerritWidgetCallback(FocusWidget widget) {
      this.widget = widget;
    }

    @Override
    public void onFailure(Throwable caught) {
      widget.setEnabled(true);
      super.onFailure(caught);
    }
  }

  public static class IgnoreErrorCallback implements AsyncCallback<ChangeDetail> {
    @Override
    public void onSuccess(ChangeDetail detail) {
      setChangeDetail(detail);
    }

    @Override
    public void onFailure(Throwable caught) {
    }
  }

  public static void setChangeDetail(ChangeDetail detail) {
    Change.Id chgId = detail.getChange().getId();
    ChangeCache.get(chgId).getChangeDetailCache().set(detail);
  }

  private final Change.Id changeId;

  public ChangeDetailCache(final Change.Id chg) {
    changeId = chg;
  }

  public void refresh() {
    Util.DETAIL_SVC.changeDetail(changeId, new GerritCallback());
  }
}
