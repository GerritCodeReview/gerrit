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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.reviewdb.client.Change;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Image;
import com.google.gwtjsonrpc.client.VoidResult;

public class StarCache implements HasValueChangeHandlers<Boolean> {
  public class KeyCommand extends NeedsSignInKeyCommand {
    public KeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      StarCache.this.toggleStar();
    }
  }

  ChangeCache cache;

  private HandlerManager manager = new HandlerManager(this);

  public StarCache(final Change.Id chg) {
    cache = ChangeCache.get(chg);
  }

  public boolean get() {
    ChangeDetail detail = cache.getChangeDetailCache().get();
    if (detail != null) {
      return detail.isStarred();
    }
    ChangeInfo info = cache.getChangeInfoCache().get();
    if (info != null) {
      return info.isStarred();
    }
    return false;
  }

  public void set(final boolean s) {
    if (Gerrit.isSignedIn() && s != get()) {
      final ToggleStarRequest req = new ToggleStarRequest();
      req.toggle(cache.getChangeId(), s);

      Util.LIST_SVC.toggleStars(req, new GerritCallback<VoidResult>() {
        public void onSuccess(final VoidResult result) {
          setStarred(s);
          fireEvent(new ValueChangeEvent<Boolean>(s){});
        }
      });
    }
  }

  private void setStarred(final boolean s) {
    ChangeDetail detail = cache.getChangeDetailCache().get();
    if (detail != null) {
      detail.setStarred(s);
    }
    ChangeInfo info = cache.getChangeInfoCache().get();
    if (info != null) {
      info.setStarred(s);
    }
  }

  public void toggleStar() {
    set(!get());
  }

  public Image createStar() {
    final Image star = new Image(getResource());
    star.setVisible(Gerrit.isSignedIn());

    star.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        StarCache.this.toggleStar();
      }
    });

    ValueChangeHandler starUpdater = new ValueChangeHandler() {
        @Override
        public void onValueChange(ValueChangeEvent event) {
          star.setResource(StarCache.this.getResource());
        }
      };

    cache.getChangeDetailCache().addValueChangeHandler(starUpdater);
    cache.getChangeInfoCache().addValueChangeHandler(starUpdater);

    this.addValueChangeHandler(starUpdater);

    return star;
  }

  private ImageResource getResource() {
    return get() ? Gerrit.RESOURCES.starFilled() : Gerrit.RESOURCES.starOpen();
  }

  public void fireEvent(GwtEvent<?> event) {
    manager.fireEvent(event);
  }

  public HandlerRegistration addValueChangeHandler(
      ValueChangeHandler<Boolean> handler) {
    return manager.addHandler(ValueChangeEvent.getType(), handler);
  }
}
