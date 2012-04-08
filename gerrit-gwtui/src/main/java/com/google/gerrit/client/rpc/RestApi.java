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

import com.google.gerrit.client.RpcStatus;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.ServerUnavailableException;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.JsonConstants;

/** Makes a REST API call to the server. */
public class RestApi {
  /**
   * Expected JSON content body prefix that prevents XSSI.
   * <p>
   * The server always includes this line as the first line of the response
   * content body when the response body is formatted as JSON. It gets inserted
   * by the server to prevent the resource from being imported into another
   * domain's page using a &lt;script&gt; tag. This line must be removed before
   * the JSON can be parsed.
   */
  private static final String JSON_MAGIC = ")]}'\n";

  private StringBuilder url;
  private boolean hasQueryParams;

  /**
   * Initialize a new API call.
   * <p>
   * By default the JSON format will be selected by including an HTTP Accept
   * header in the request.
   *
   * @param name URL of the REST resource to access, e.g. {@code "/projects/"}
   *        to list accessible projects from the server.
   */
  public RestApi(String name) {
    if (name.startsWith("/")) {
      name = name.substring(1);
    }

    url = new StringBuilder();
    url.append(GWT.getHostPageBaseURL());
    url.append(name);
  }

  public RestApi addParameter(String name, String value) {
    return addParameterRaw(name, URL.encodeQueryString(value));
  }

  public RestApi addParameterTrue(String name) {
    return addParameterRaw(name, null);
  }

  public RestApi addParameter(String name, boolean value) {
    return addParameterRaw(name, value ? "t" : "f");
  }

  public RestApi addParameter(String name, int value) {
    return addParameterRaw(name, String.valueOf(value));
  }

  public RestApi addParameter(String name, Enum<?> value) {
    return addParameterRaw(name, value.name());
  }

  public RestApi addParameterRaw(String name, String value) {
    if (hasQueryParams) {
      url.append("&");
    } else {
      url.append("?");
      hasQueryParams = true;
    }
    url.append(name);
    if (value != null) {
      url.append("=").append(value);
    }
    return this;
  }

  public <T extends JavaScriptObject> void send(final AsyncCallback<T> cb) {
    RequestBuilder req = new RequestBuilder(RequestBuilder.GET, url.toString());
    req.setHeader("Accept", JsonConstants.JSON_TYPE);
    req.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request req, Response res) {
        RpcStatus.INSTANCE.onRpcComplete();
        int status = res.getStatusCode();
        if (status != 200) {
          if ((400 <= status && status < 500) && isTextBody(res)) {
            cb.onFailure(new RemoteJsonException(res.getText(), status, null));
          } else {
            cb.onFailure(new StatusCodeException(status, res.getStatusText()));
          }
          return;
        }

        if (!isJsonBody(res)) {
          cb.onFailure(new RemoteJsonException("Invalid JSON"));
          return;
        }

        String json = res.getText();
        if (!json.startsWith(JSON_MAGIC)) {
          cb.onFailure(new RemoteJsonException("Invalid JSON"));
          return;
        }

        T data;
        try {
          data = Natives.parseJSON(json.substring(JSON_MAGIC.length()));
        } catch (RuntimeException e) {
          cb.onFailure(new RemoteJsonException("Invalid JSON"));
          return;
        }

        cb.onSuccess(data);
      }

      @Override
      public void onError(Request req, Throwable err) {
        RpcStatus.INSTANCE.onRpcComplete();
        if (err.getMessage().contains("XmlHttpRequest.status")) {
          cb.onFailure(new ServerUnavailableException());
        } else {
          cb.onFailure(err);
        }
      }
    });
    try {
      RpcStatus.INSTANCE.onRpcStart();
      req.send();
    } catch (RequestException e) {
      RpcStatus.INSTANCE.onRpcComplete();
      cb.onFailure(e);
    }
  }

  private static boolean isJsonBody(Response res) {
    return isContentType(res, JsonConstants.JSON_TYPE);
  }

  private static boolean isTextBody(Response res) {
    return isContentType(res, "text/plain");
  }

  private static boolean isContentType(Response res, String want) {
    String type = res.getHeader("Content-Type");
    if (type == null) {
      return false;
    }
    int semi = type.indexOf(';');
    if (semi >= 0) {
      type = type.substring(0, semi).trim();
    }
    return want.equals(type);
  }
}
