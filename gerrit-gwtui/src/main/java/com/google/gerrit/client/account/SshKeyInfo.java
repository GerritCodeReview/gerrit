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

package com.google.gerrit.client.account;

import com.google.gwt.core.client.JavaScriptObject;

public class SshKeyInfo extends JavaScriptObject {
  public final native int seq() /*-{ return this.seq || 0; }-*/;

  public final native String sshPublicKey() /*-{ return this.ssh_public_key; }-*/;

  public final native String encodedKey() /*-{ return this.encoded_key; }-*/;

  public final native String algorithm() /*-{ return this.algorithm; }-*/;

  public final native String comment() /*-{ return this.comment; }-*/;

  public final native boolean isValid() /*-{ return this['valid'] ? true : false; }-*/;

  protected SshKeyInfo() {}
}
