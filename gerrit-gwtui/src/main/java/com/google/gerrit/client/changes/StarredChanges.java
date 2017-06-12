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
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Image;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.HandlerRegistration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Supports the star icon displayed on changes and tracking the status. */
public class StarredChanges {
  private static final Event.Type<ChangeStarHandler> TYPE = new Event.Type<>();

  /** Handler that can receive notifications of a change's starred status. */
  public interface ChangeStarHandler {
    void onChangeStar(ChangeStarEvent event);
  }

  /** Event fired when a star changes status. The new status is reported. */
  public static class ChangeStarEvent extends Event<ChangeStarHandler> {
    private boolean starred;

    public ChangeStarEvent(Change.Id source, boolean starred) {
      setSource(source);
      this.starred = starred;
    }

    public boolean isStarred() {
      return starred;
    }

    @Override
    public Type<ChangeStarHandler> getAssociatedType() {
      return TYPE;
    }

    @Override
    protected void dispatch(ChangeStarHandler handler) {
      handler.onChangeStar(this);
    }
  }

  /**
   * Create a star icon for the given change, and current status. Returns null if the user is not
   * signed in and cannot support starred changes.
   */
  public static Icon createIcon(Change.Id source, boolean starred) {
    return Gerrit.isSignedIn() ? new Icon(source, starred) : null;
  }

  /** Make a key command that toggles the star for a change. */
  public static KeyCommand newKeyCommand(final Icon icon) {
    return new KeyCommand(0, 's', Util.C.changeTableStar()) {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        icon.toggleStar();
      }
    };
  }

  /** Add a handler to listen for starred status to change. */
  public static HandlerRegistration addHandler(Change.Id source, ChangeStarHandler handler) {
    return Gerrit.EVENT_BUS.addHandlerToSource(TYPE, source, handler);
  }

  /**
   * Broadcast the current starred value of a change to UI widgets. This does not RPC to the server
   * and does not alter the starred status of a change.
   */
  public static void fireChangeStarEvent(Change.Id id, boolean starred) {
    Gerrit.EVENT_BUS.fireEventFromSource(new ChangeStarEvent(id, starred), id);
  }

  /**
   * Set the starred status of a change. This method broadcasts to all interested UI widgets and
   * sends an RPC to the server to record the updated status.
   */
  public static void toggleStar(final Change.Id changeId, final boolean newValue) {
    pending.put(changeId, newValue);
    fireChangeStarEvent(changeId, newValue);
    if (!busy) {
      startRequest();
    }
  }

  private static boolean busy;
  private static final Map<Change.Id, Boolean> pending = new LinkedHashMap<>(4);

  private static void startRequest() {
    busy = true;

    final Change.Id id = pending.keySet().iterator().next();
    final boolean starred = pending.remove(id);
    RestApi call = AccountApi.self().view("starred.changes").id(id.get());
    AsyncCallback<JavaScriptObject> cb =
        new AsyncCallback<JavaScriptObject>() {
          @Override
          public void onSuccess(JavaScriptObject none) {
            if (pending.isEmpty()) {
              busy = false;
            } else {
              startRequest();
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            if (!starred && RestApi.isStatus(caught, 404)) {
              onSuccess(null);
              return;
            }

            fireChangeStarEvent(id, !starred);
            for (Map.Entry<Change.Id, Boolean> e : pending.entrySet()) {
              fireChangeStarEvent(e.getKey(), !e.getValue());
            }
            pending.clear();
            busy = false;
          }
        };
    if (starred) {
      call.put(cb);
    } else {
      call.delete(cb);
    }
  }

  public static class Icon extends Image implements ChangeStarHandler, ClickHandler {
    private final Change.Id changeId;
    private boolean starred;
    private HandlerRegistration handler;

    Icon(Change.Id changeId, boolean starred) {
      super(resource(starred));
      this.changeId = changeId;
      this.starred = starred;
      addClickHandler(this);
    }

    /**
     * Toggles the state of the star, as if the user clicked on the image. This will broadcast the
     * new star status to all interested UI widgets, and RPC to the server to store the changed
     * value.
     */
    public void toggleStar() {
      StarredChanges.toggleStar(changeId, !starred);
    }

    @Override
    protected void onLoad() {
      handler = StarredChanges.addHandler(changeId, this);
    }

    @Override
    protected void onUnload() {
      handler.removeHandler();
      handler = null;
    }

    @Override
    public void onChangeStar(ChangeStarEvent event) {
      setResource(resource(event.isStarred()));
      starred = event.isStarred();
    }

    @Override
    public void onClick(ClickEvent event) {
      toggleStar();
    }

    private static ImageResource resource(boolean starred) {
      return starred ? Gerrit.RESOURCES.starFilled() : Gerrit.RESOURCES.starOpen();
    }
  }

  private StarredChanges() {}
}
