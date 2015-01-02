// Copyright (C) 2014 The Android Open Source Project
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

public class HttpResponse<T extends JavaScriptObject> {
  private final Response httpResponse;
  private final T result;

  HttpResponse(Response httpResponse, T result) {
    this.httpResponse = httpResponse;
    this.result = result;
  }

  public String getHeader(String header) {
    return httpResponse.getHeader(header);
  }

  public T result() {
    return result;
  }
}
