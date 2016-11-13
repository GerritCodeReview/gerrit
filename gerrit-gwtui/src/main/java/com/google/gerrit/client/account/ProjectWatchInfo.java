// Copyright (C) 2016 The Android Open Source Project
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
package com.google.gerrit.client.account;

import com.google.gwt.core.client.JavaScriptObject;

public class ProjectWatchInfo extends JavaScriptObject {

  public enum Type {
    NEW_CHANGES,
    NEW_PATCHSETS,
    ALL_COMMENTS,
    SUBMITTED_CHANGES,
    ABANDONED_CHANGES
  }

  public final native String project() /*-{ return this.project; }-*/;

  public final native String filter() /*-{ return this.filter; }-*/;

  public final native void project(String s) /*-{ this.project = s; }-*/;

  public final native void filter(String s) /*-{ this.filter = s; }-*/;

  public final void notify(ProjectWatchInfo.Type t, Boolean b) {
    if (t == ProjectWatchInfo.Type.NEW_CHANGES) {
      notifyNewChanges(b.booleanValue());
    } else if (t == Type.NEW_PATCHSETS) {
      notifyNewPatchSets(b.booleanValue());
    } else if (t == Type.ALL_COMMENTS) {
      notifyAllComments(b.booleanValue());
    } else if (t == Type.SUBMITTED_CHANGES) {
      notifySubmittedChanges(b.booleanValue());
    } else if (t == Type.ABANDONED_CHANGES) {
      notifyAbandonedChanges(b.booleanValue());
    }
  }

  public final Boolean notify(ProjectWatchInfo.Type t) {
    boolean b = false;
    if (t == ProjectWatchInfo.Type.NEW_CHANGES) {
      b = notifyNewChanges();
    } else if (t == Type.NEW_PATCHSETS) {
      b = notifyNewPatchSets();
    } else if (t == Type.ALL_COMMENTS) {
      b = notifyAllComments();
    } else if (t == Type.SUBMITTED_CHANGES) {
      b = notifySubmittedChanges();
    } else if (t == Type.ABANDONED_CHANGES) {
      b = notifyAbandonedChanges();
    }
    return Boolean.valueOf(b);
  }

  private native boolean
      notifyNewChanges() /*-{ return this['notify_new_changes'] ? true : false; }-*/;

  private native boolean
      notifyNewPatchSets() /*-{ return this['notify_new_patch_sets'] ? true : false; }-*/;

  private native boolean
      notifyAllComments() /*-{ return this['notify_all_comments'] ? true : false; }-*/;

  private native boolean
      notifySubmittedChanges() /*-{ return this['notify_submitted_changes'] ? true : false; }-*/;

  private native boolean
      notifyAbandonedChanges() /*-{ return this['notify_abandoned_changes'] ? true : false; }-*/;

  private native void notifyNewChanges(
      boolean b) /*-{ this['notify_new_changes'] = b ? true : null; }-*/;

  private native void notifyNewPatchSets(
      boolean b) /*-{ this['notify_new_patch_sets'] = b ? true : null; }-*/;

  private native void notifyAllComments(
      boolean b) /*-{ this['notify_all_comments'] = b ? true : null; }-*/;

  private native void notifySubmittedChanges(
      boolean b) /*-{ this['notify_submitted_changes'] = b ? true : null; }-*/;

  private native void notifyAbandonedChanges(
      boolean b) /*-{ this['notify_abandoned_changes'] = b ? true : null; }-*/;

  protected ProjectWatchInfo() {}
}
