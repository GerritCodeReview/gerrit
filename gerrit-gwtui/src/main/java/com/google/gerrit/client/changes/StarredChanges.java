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
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Image;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.HandlerRegistration;

/** Supports the star icon displayed on changes and tracking the status. */
public class StarredChanges {
  private static final EventBus eventBus = new SimpleEventBus();
  private static final Event.Type<ChangeStarHandler> TYPE =
      new Event.Type<ChangeStarHandler>();

  /** Handler that can receive notifications of a change's starred status. */
  public static interface ChangeStarHandler {
    public void onChangeStar(ChangeStarEvent event);
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
   * Create a star icon for the given change, and current status. Returns null
   * if the user is not signed in and cannot support starred changes.
   */
  public static Icon createIcon(Change.Id source, boolean starred) {
    return Gerrit.isSignedIn() ? new Icon(source, starred) : null;
  }

  /** Add a handler to listen for starred status to change. */
  public static HandlerRegistration addHandler(
      Change.Id source,
      ChangeStarHandler handler) {
    return eventBus.addHandlerToSource(TYPE, source, handler);
  }

  /**
   * Broadcast the current starred value of a change to UI widgets. This does
   * not RPC to the server and does not alter the starred status of a change.
   */
  public static void fireChangeStarEvent(Change.Id id, boolean starred) {
    eventBus.fireEventFromSource(
        new ChangeStarEvent(id, starred),
        id);
  }

  /**
   * Set the starred status of a change. This method broadcasts to all
   * interested UI widgets and sends an RPC to the server to record the
   * updated status.
   */
  public static void toggleStar(
      final Change.Id changeId,
      final boolean newValue) {
    if (next == null) {
      next = new ToggleStarRequest();
    }
    next.toggle(changeId, newValue);
    fireChangeStarEvent(changeId, newValue);
    if (!busy) {
      start();
    }
  }

  private static ToggleStarRequest next;
  private static boolean busy;

  private static void start() {
    final ToggleStarRequest req = next;
    next = null;
    busy = true;

    Util.LIST_SVC.toggleStars(req, new GerritCallback<VoidResult>() {
      @Override
      public void onSuccess(VoidResult result) {
        if (next != null) {
          start();
        } else {
          busy = false;
        }
      }

      @Override
      public void onFailure(Throwable caught) {
        rollback(req);
        if (next != null) {
          rollback(next);
          next = null;
        }
        busy = false;
        super.onFailure(caught);
      }
    });
  }

  private static void rollback(ToggleStarRequest req) {
    if (req.getAddSet() != null) {
      for (Change.Id id : req.getAddSet()) {
        fireChangeStarEvent(id, false);
      }
    }
    if (req.getRemoveSet() != null) {
      for (Change.Id id : req.getRemoveSet()) {
        fireChangeStarEvent(id, true);
      }
    }
  }

  public static class Icon extends Image
      implements ChangeStarHandler, ClickHandler {
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
     * Toggles the state of the star, as if the user clicked on the image. This
     * will broadcast the new star status to all interested UI widgets, and RPC
     * to the server to store the changed value.
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

  private StarredChanges() {
  }
}
