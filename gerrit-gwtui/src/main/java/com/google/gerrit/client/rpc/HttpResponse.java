// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Response;

public class HttpResponse<T extends JavaScriptObject> extends JavaScriptObject {
  static native <T extends JavaScriptObject> HttpResponse<T> wrap(
      Response a,
      T b) /*-{
    return {response: a, result: b}
  }-*/;

  public final String getHeader(String header) {
    return response().getHeader(header);
  }

  public final native T result() /*-{ return this.result }-*/;
  private final native Response response() /*-{ return this.response }-*/;

  protected HttpResponse() {
  }
}
