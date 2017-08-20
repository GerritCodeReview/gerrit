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
import com.google.gwt.user.client.ui.Widget;

/**
 * Widget to display within a {@link ViewSite}.
 *
 * <p>Implementations must override {@code protected void onLoad()} and arrange for {@link
 * #display()} to be invoked once the DOM within the view is consistent for presentation to the
 * user. Typically this means that the subclass can start RPCs within {@code onLoad()} and then
 * invoke {@code display()} from within the AsyncCallback's {@code onSuccess(Object)} method.
 */
public abstract class View extends Composite {
  ViewSite<? extends View> site;

  @Override
  protected void onUnload() {
    site = null;
    super.onUnload();
  }

  /** true if this is the current view of its parent view site */
  public final boolean isCurrentView() {
    Widget p = getParent();
    while (p != null) {
      if (p instanceof ViewSite<?>) {
        return ((ViewSite<?>) p).getView() == this;
      }
      p = p.getParent();
    }
    return false;
  }

  /** Replace the current view in the parent ViewSite with this view. */
  public final void display() {
    if (site != null) {
      site.swap(this);
    }
  }
}
