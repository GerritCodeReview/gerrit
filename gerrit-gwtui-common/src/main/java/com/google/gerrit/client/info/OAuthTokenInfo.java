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

package com.google.gerrit.client.info;

import com.google.gwt.core.client.JavaScriptObject;

public class OAuthTokenInfo extends JavaScriptObject {

  protected OAuthTokenInfo() {}

  public final native String username() /*-{ return this.username; }-*/;

  public final native String resourceHost() /*-{ return this.resource_host; }-*/;

  public final native String accessToken() /*-{ return this.access_token; }-*/;

  public final native String providerId() /*-{ return this.provider_id; }-*/;

  public final native String expiresAt() /*-{ return this.expires_at; }-*/;

  public final native String type() /*-{ return this.type; }-*/;
}
