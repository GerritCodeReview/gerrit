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
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwtexpui.safehtml.client.FindReplace;
import com.google.gwtexpui.safehtml.client.LinkFindReplace;
import com.google.gwtexpui.safehtml.client.RawFindReplace;
import java.util.ArrayList;
import java.util.List;

public class ConfigInfo extends JavaScriptObject {

  public final native String description() /*-{ return this.description }-*/;

  public final native InheritedBooleanInfo requireChangeId()
      /*-{ return this.require_change_id; }-*/ ;

  public final native InheritedBooleanInfo useContentMerge()
      /*-{ return this.use_content_merge; }-*/ ;

  public final native InheritedBooleanInfo useContributorAgreements()
      /*-{ return this.use_contributor_agreements; }-*/ ;

  public final native InheritedBooleanInfo createNewChangeForAllNotInTarget()
      /*-{ return this.create_new_change_for_all_not_in_target; }-*/ ;

  public final native InheritedBooleanInfo useSignedOffBy()
      /*-{ return this.use_signed_off_by; }-*/ ;

  public final native InheritedBooleanInfo enableSignedPush()
      /*-{ return this.enable_signed_push; }-*/ ;

  public final native InheritedBooleanInfo requireSignedPush()
      /*-{ return this.require_signed_push; }-*/ ;

  public final native InheritedBooleanInfo rejectImplicitMerges()
      /*-{ return this.reject_implicit_merges; }-*/ ;

  public final SubmitType submitType() {
    return SubmitType.valueOf(submitTypeRaw());
  }

  public final native NativeMap<NativeMap<ConfigParameterInfo>> pluginConfig()
      /*-{ return this.plugin_config || {}; }-*/ ;

  public final native NativeMap<ConfigParameterInfo> pluginConfig(String p)
      /*-{ return this.plugin_config[p]; }-*/ ;

  public final native NativeMap<ActionInfo> actions() /*-{ return this.actions; }-*/;

  private native String submitTypeRaw() /*-{ return this.submit_type }-*/;

  public final ProjectState state() {
    if (stateRaw() == null) {
      return ProjectState.ACTIVE;
    }
    return ProjectState.valueOf(stateRaw());
  }

  private native String stateRaw() /*-{ return this.state }-*/;

  public final native MaxObjectSizeLimitInfo maxObjectSizeLimit()
      /*-{ return this.max_object_size_limit; }-*/ ;

  private native NativeMap<CommentLinkInfo> commentlinks0() /*-{ return this.commentlinks; }-*/;

  final List<FindReplace> commentlinks() {
    JsArray<CommentLinkInfo> cls = commentlinks0().values();
    List<FindReplace> commentLinks = new ArrayList<>(cls.length());
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
          new ErrorDialog(
                  "Invalid commentlink configuration: "
                      + (index == -1 ? e.getMessage() : e.getMessage().substring(0, index)))
              .center();
        }
      }
    }
    return commentLinks;
  }

  final native ThemeInfo theme() /*-{ return this.theme; }-*/;

  protected ConfigInfo() {}

  static class CommentLinkInfo extends JavaScriptObject {
    final native String match() /*-{ return this.match; }-*/;

    final native String link() /*-{ return this.link; }-*/;

    final native String html() /*-{ return this.html; }-*/;

    final native boolean enabled() /*-{
      return !this.hasOwnProperty('enabled') || this.enabled;
    }-*/;

    protected CommentLinkInfo() {}
  }

  public static class InheritedBooleanInfo extends JavaScriptObject {
    public static InheritedBooleanInfo create() {
      return (InheritedBooleanInfo) createObject();
    }

    public final native boolean value() /*-{ return this.value ? true : false; }-*/;

    public final native boolean inheritedValue()
        /*-{ return this.inherited_value ? true : false; }-*/ ;

    public final InheritableBoolean configuredValue() {
      return InheritableBoolean.valueOf(configuredValueRaw());
    }

    private native String configuredValueRaw() /*-{ return this.configured_value }-*/;

    public final void setConfiguredValue(InheritableBoolean v) {
      setConfiguredValueRaw(v.name());
    }

    public final native void setConfiguredValueRaw(String v)
        /*-{ if(v)this.configured_value=v; }-*/ ;

    protected InheritedBooleanInfo() {}
  }

  public static class MaxObjectSizeLimitInfo extends JavaScriptObject {
    public final native String value() /*-{ return this.value; }-*/;

    public final native String inheritedValue() /*-{ return this.inherited_value; }-*/;

    public final native String configuredValue() /*-{ return this.configured_value }-*/;

    protected MaxObjectSizeLimitInfo() {}
  }

  public static class ConfigParameterInfo extends JavaScriptObject {
    public final native String name() /*-{ return this.name; }-*/;

    public final native String displayName() /*-{ return this.display_name; }-*/;

    public final native String description() /*-{ return this.description; }-*/;

    public final native String warning() /*-{ return this.warning; }-*/;

    public final native String type() /*-{ return this.type; }-*/;

    public final native String value() /*-{ return this.value; }-*/;

    public final native boolean editable() /*-{ return this.editable ? true : false; }-*/;

    public final native boolean inheritable() /*-{ return this.inheritable ? true : false; }-*/;

    public final native String configuredValue() /*-{ return this.configured_value; }-*/;

    public final native String inheritedValue() /*-{ return this.inherited_value; }-*/;

    public final native JsArrayString permittedValues() /*-{ return this.permitted_values; }-*/;

    public final native JsArrayString values() /*-{ return this.values; }-*/;

    protected ConfigParameterInfo() {}
  }

  public static class ConfigParameterValue extends JavaScriptObject {
    final native void init() /*-{ this.values = []; }-*/;

    final native void addValue(String v) /*-{ this.values.push(v); }-*/;

    final native void setValue(String v) /*-{ if(v)this.value = v; }-*/;

    public static ConfigParameterValue create() {
      ConfigParameterValue v = createObject().cast();
      return v;
    }

    public final ConfigParameterValue values(String[] values) {
      init();
      for (String v : values) {
        addValue(v);
      }
      return this;
    }

    public final ConfigParameterValue value(String v) {
      setValue(v);
      return this;
    }

    protected ConfigParameterValue() {}
  }
}
