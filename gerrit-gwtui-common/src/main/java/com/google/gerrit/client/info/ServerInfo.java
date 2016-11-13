// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.info;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import java.util.HashMap;
import java.util.Map;

public class ServerInfo extends JavaScriptObject {
  public final native AuthInfo auth() /*-{ return this.auth; }-*/;

  public final native ChangeConfigInfo change() /*-{ return this.change; }-*/;

  public final native DownloadInfo download() /*-{ return this.download; }-*/;

  public final native GerritInfo gerrit() /*-{ return this.gerrit; }-*/;

  public final native PluginConfigInfo plugin() /*-{ return this.plugin; }-*/;

  public final native SshdInfo sshd() /*-{ return this.sshd; }-*/;

  public final native SuggestInfo suggest() /*-{ return this.suggest; }-*/;

  public final native UserConfigInfo user() /*-{ return this.user; }-*/;

  public final native ReceiveInfo receive() /*-{ return this.receive; }-*/;

  public final Map<String, String> urlAliases() {
    Map<String, String> urlAliases = new HashMap<>();
    for (String k : Natives.keys(_urlAliases())) {
      urlAliases.put(k, urlAliasToken(k));
    }
    return urlAliases;
  }

  public final native String urlAliasToken(String n) /*-{ return this.url_aliases[n]; }-*/;

  private native NativeMap<NativeString> _urlAliases() /*-{ return this.url_aliases; }-*/;

  public final boolean hasSshd() {
    return sshd() != null;
  }

  protected ServerInfo() {}

  public static class ChangeConfigInfo extends JavaScriptObject {
    public final native boolean allowDrafts() /*-{ return this.allow_drafts || false; }-*/;

    public final native boolean allowBlame() /*-{ return this.allow_blame || false; }-*/;

    public final native int largeChange() /*-{ return this.large_change || 0; }-*/;

    public final native String replyLabel() /*-{ return this.reply_label; }-*/;

    public final native String replyTooltip() /*-{ return this.reply_tooltip; }-*/;

    public final native boolean showAssignee() /*-{ return this.show_assignee || false; }-*/;

    public final native int updateDelay() /*-{ return this.update_delay || 0; }-*/;

    public final native boolean isSubmitWholeTopicEnabled() /*-{
        return this.submit_whole_topic; }-*/;

    protected ChangeConfigInfo() {}
  }

  public static class PluginConfigInfo extends JavaScriptObject {
    public final native boolean hasAvatars() /*-{ return this.has_avatars || false; }-*/;

    public final native JsArrayString jsResourcePaths() /*-{
        return this.js_resource_paths || []; }-*/;

    protected PluginConfigInfo() {}
  }

  public static class SshdInfo extends JavaScriptObject {
    protected SshdInfo() {}
  }

  public static class SuggestInfo extends JavaScriptObject {
    public final native int from() /*-{ return this.from || 0; }-*/;

    protected SuggestInfo() {}
  }

  public static class UserConfigInfo extends JavaScriptObject {
    public final native String anonymousCowardName() /*-{ return this.anonymous_coward_name; }-*/;

    protected UserConfigInfo() {}
  }

  public static class ReceiveInfo extends JavaScriptObject {
    public final native boolean enableSignedPush()
        /*-{ return this.enable_signed_push || false; }-*/ ;

    protected ReceiveInfo() {}
  }
}
