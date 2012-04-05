// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwtjsonrpc.client.JsonUtil;
import com.google.gwtjsonrpc.client.ServerUnavailableException;

public class RestApi {
  private static final String MAGIC_PREFIX = ")]}'\n";

  private StringBuilder url;

  public RestApi(String name) {
    if (name.startsWith("/")) {
      name = name.substring(1);
    }

    url = new StringBuilder();
    url.append(GWT.getHostPageBaseURL());
    url.append(name);
  }

  public RestApi addParameter(String name, String value) {
    if (url.indexOf("?") < 0) {
      url.append("?");
    } else {
      url.append("&");
    }
    url.append(name).append("=").append(value);
    return this;
  }

  public <T extends JavaScriptObject> void send(final AsyncCallback<T> cb) {
    RequestBuilder req = new RequestBuilder(RequestBuilder.GET, url.toString());
    req.setHeader("Accept", "application/json");
    req.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request req, Response res) {
        int sc = res.getStatusCode();
        if (200 == sc && isJsonBody(res)) {
          String json = res.getText();
          if (json.startsWith(MAGIC_PREFIX)) {
            json = json.substring(MAGIC_PREFIX.length());
          }
          cb.onSuccess((T) parse(json));
        } else {
          cb.onFailure(new StatusCodeException(sc, res.getStatusText()));
        }
      }

      @Override
      public void onError(Request req, Throwable err) {
        if (err.getMessage().contains("XmlHttpRequest.status")) {
          cb.onFailure(new ServerUnavailableException());
        } else {
          cb.onFailure(err);
        }
      }
    });
    try {
      req.send();
    } catch (RequestException e) {
      cb.onFailure(e);
    }
  }

  private static boolean isJsonBody(Response res) {
    String type = res.getHeader("Content-Type");
    if (type == null) {
      return false;
    }
    int semi = type.indexOf(';');
    if (semi >= 0) {
      type = type.substring(0, semi).trim();
    }
    return JsonUtil.JSON_TYPE.equals(type);
  }

  private static native JavaScriptObject parse(String jsonStr) /*-{ return eval(jsonStr); }-*/;
}
