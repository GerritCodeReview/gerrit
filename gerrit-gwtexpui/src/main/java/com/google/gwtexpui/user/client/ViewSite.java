// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.user.client;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;

/**
 * Hosts a single {@link View}.
 *
 * <p>View instances are attached inside of an invisible DOM node, permitting their {@code onLoad()}
 * method to be invoked and to update the DOM prior to the elements being made visible in the UI.
 *
 * <p>Complaint View instances must invoke {@link View#display()} once the DOM is ready for
 * presentation.
 */
public class ViewSite<V extends View> extends Composite {
  private final FlowPanel main;
  private SimplePanel current;
  private SimplePanel next;

  public ViewSite() {
    main = new FlowPanel();
    initWidget(main);
  }

  /** Get the current view; null if there is no view being displayed. */
  @SuppressWarnings("unchecked")
  public V getView() {
    return current != null ? (V) current.getWidget() : null;
  }

  /**
   * Set the next view to display.
   *
   * <p>The view will be attached to the DOM tree within a hidden container, permitting its {@code
   * onLoad()} method to execute and update the DOM without the user seeing the result.
   *
   * @param view the next view to display.
   */
  public void setView(final V view) {
    if (next != null) {
      main.remove(next);
    }
    view.site = this;
    next = new SimplePanel();
    next.setVisible(false);
    main.add(next);
    next.add(view);
  }

  /**
   * Invoked after the view becomes the current view and has been made visible.
   *
   * @param view the view being displayed.
   */
  protected void onShowView(final V view) {}

  @SuppressWarnings("unchecked")
  final void swap(final View v) {
    if (next != null && next.getWidget() == v) {
      if (current != null) {
        main.remove(current);
      }
      current = next;
      next = null;
      current.setVisible(true);
      onShowView((V) v);
    }
  }
}
