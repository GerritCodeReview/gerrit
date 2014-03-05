// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.projects;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwtexpui.safehtml.client.FindReplace;
import com.google.gwtexpui.safehtml.client.LinkFindReplace;
import com.google.gwtexpui.safehtml.client.RawFindReplace;

import java.util.ArrayList;
import java.util.List;

public class ConfigInfo extends JavaScriptObject {

  public final native String description()
  /*-{ return this.description }-*/;

  public final native InheritedBooleanInfo require_change_id()
  /*-{ return this.require_change_id; }-*/;

  public final native InheritedBooleanInfo use_content_merge()
  /*-{ return this.use_content_merge; }-*/;

  public final native InheritedBooleanInfo use_contributor_agreements()
  /*-{ return this.use_contributor_agreements; }-*/;

  public final native InheritedBooleanInfo use_signed_off_by()
  /*-{ return this.use_signed_off_by; }-*/;

  public final SubmitType submit_type() {
    return SubmitType.valueOf(submit_typeRaw());
  }

  public final native NativeMap<ActionInfo> actions()
  /*-{ return this.actions; }-*/;

  private final native String submit_typeRaw()
  /*-{ return this.submit_type }-*/;

  public final Project.State state() {
    if (stateRaw() == null) {
      return Project.State.ACTIVE;
    }
    return Project.State.valueOf(stateRaw());
  }
  private final native String stateRaw()
  /*-{ return this.state }-*/;

  public final native MaxObjectSizeLimitInfo max_object_size_limit()
  /*-{ return this.max_object_size_limit; }-*/;

  private final native NativeMap<CommentLinkInfo> commentlinks0()
  /*-{ return this.commentlinks; }-*/;
  final List<FindReplace> commentlinks() {
    JsArray<CommentLinkInfo> cls = commentlinks0().values();
    List<FindReplace> commentLinks = new ArrayList<FindReplace>(cls.length());
    for (int i = 0; i < cls.length(); i++) {
      CommentLinkInfo cl = cls.get(i);
      if (!cl.enabled()) {
        continue;
      }
      if (cl.link() != null) {
        commentLinks.add(new LinkFindReplace(cl.match(), cl.link()));
      } else {
        try {
          FindReplace fr = new RawFindReplace(cl.match(), cl.html());
          commentLinks.add(fr);
        } catch (RuntimeException e) {
          int index = e.getMessage().indexOf("at Object");
          new ErrorDialog("Invalid commentlink configuration: "
              + (index == -1
              ? e.getMessage()
              : e.getMessage().substring(0, index))).center();
        }
      }
    }
    return commentLinks;
  }

  final native ThemeInfo theme() /*-{ return this.theme; }-*/;

  protected ConfigInfo() {
  }

  static class CommentLinkInfo extends JavaScriptObject {
    final native String match() /*-{ return this.match; }-*/;
    final native String link() /*-{ return this.link; }-*/;
    final native String html() /*-{ return this.html; }-*/;
    final native boolean enabled() /*-{
      return !this.hasOwnProperty('enabled') || this.enabled;
    }-*/;

    protected CommentLinkInfo() {
    }
  }

  public static class InheritedBooleanInfo extends JavaScriptObject {
    public static InheritedBooleanInfo create() {
      return (InheritedBooleanInfo) createObject();
    }

    public final native boolean value()
    /*-{ return this.value ? true : false; }-*/;

    public final native boolean inherited_value()
    /*-{ return this.inherited_value ? true : false; }-*/;

    public final InheritableBoolean configured_value() {
      return InheritableBoolean.valueOf(configured_valueRaw());
    }
    private final native String configured_valueRaw()
    /*-{ return this.configured_value }-*/;

    public final void setConfiguredValue(InheritableBoolean v) {
      setConfiguredValueRaw(v.name());
    }
    public final native void setConfiguredValueRaw(String v)
    /*-{ if(v)this.configured_value=v; }-*/;

    protected InheritedBooleanInfo() {
    }
  }

  public static class MaxObjectSizeLimitInfo extends JavaScriptObject {
    public final native String value() /*-{ return this.value; }-*/;
    public final native String inherited_value() /*-{ return this.inherited_value; }-*/;
    public final native String configured_value() /*-{ return this.configured_value }-*/;

    protected MaxObjectSizeLimitInfo() {
    }
  }
}
