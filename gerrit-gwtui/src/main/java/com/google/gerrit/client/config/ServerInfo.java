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

package com.google.gerrit.client.config;

import com.google.gwt.core.client.JavaScriptObject;

public class ServerInfo extends JavaScriptObject {
  public final native AuthInfo auth() /*-{ return this.auth; }-*/;
  public final native ChangeConfigInfo change() /*-{ return this.change; }-*/;
  public final native ContactStoreInfo contactStore() /*-{ return this.contact_store; }-*/;
  public final native DownloadInfo download() /*-{ return this.download; }-*/;
  public final native GerritInfo gerrit() /*-{ return this.gerrit; }-*/;
  public final native GitWebInfo gitWeb() /*-{ return this.git_web; }-*/;
  public final native SuggestInfo suggest() /*-{ return this.suggest; }-*/;
  public final native UserConfigInfo user() /*-{ return this.user; }-*/;

  public final boolean hasContactStore() {
    return contactStore() != null;
  }

  protected ServerInfo() {
  }

  public static class ChangeConfigInfo extends JavaScriptObject {
    public final native boolean allowDrafts() /*-{ return this.allow_drafts || false; }-*/;
    public final native int largeChange() /*-{ return this.large_change || 0; }-*/;
    public final native String replyLabel() /*-{ return this.reply_label; }-*/;
    public final native String replyTooltip() /*-{ return this.reply_tooltip; }-*/;
    public final native int updateDelay() /*-{ return this.update_delay || 0; }-*/;

    protected ChangeConfigInfo() {
    }
  }

  public static class ContactStoreInfo extends JavaScriptObject {
    public final native String url() /*-{ return this.url; }-*/;

    protected ContactStoreInfo() {
    }
  }

  public static class SuggestInfo extends JavaScriptObject {
    public final native int from() /*-{ return this.from || 0; }-*/;

    protected SuggestInfo() {
    }
  }

  public static class UserConfigInfo extends JavaScriptObject {
    public final native String anonymousCowardName() /*-{ return this.anonymous_coward_name; }-*/;

    protected UserConfigInfo() {
    }
  }
}
