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

import static com.google.gwt.http.client.RequestBuilder.DELETE;
import static com.google.gwt.http.client.RequestBuilder.GET;
import static com.google.gwt.http.client.RequestBuilder.POST;
import static com.google.gwt.http.client.RequestBuilder.PUT;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.RpcStatus;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestBuilder.Method;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONException;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
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

  private class MyRequestCallback<T extends JavaScriptObject> implements
      RequestCallback {
    private final AsyncCallback<T> cb;

    MyRequestCallback(AsyncCallback<T> cb) {
      this.cb = cb;
    }

    @Override
    public void onResponseReceived(Request req, Response res) {
      int status = res.getStatusCode();
      if (status != 200) {
        String msg;
        if (isTextBody(res)) {
          msg = res.getText().trim();
        } else if (isJsonBody(res)) {
          try {
            ErrorMessage error = parseJson(res);
            msg = error.message() != null
                ? error.message()
                : res.getText().trim();
          } catch (JSONException e) {
            msg = res.getText().trim();
          }
        } else {
          msg = res.getStatusText();
        }

        Throwable error;
        if (400 <= status && status < 600) {
          error = new RemoteJsonException(msg, status, null);
        } else {
          error = new StatusCodeException(status, res.getStatusText());
        }
        RpcStatus.INSTANCE.onRpcComplete();
        cb.onFailure(error);
        return;
      }

      if (!isJsonBody(res)) {
        RpcStatus.INSTANCE.onRpcComplete();
        cb.onFailure(new RemoteJsonException("Expected "
            + JsonConstants.JSON_TYPE + "; received Content-Type: "
            + res.getHeader("Content-Type")));
        return;
      }

      T data;
      try {
        data = parseJson(res);
      } catch (JSONException e) {
        RpcStatus.INSTANCE.onRpcComplete();
        cb.onFailure(new RemoteJsonException("Invalid JSON: " + e.getMessage()));
        return;
      }

      cb.onSuccess(data);
      RpcStatus.INSTANCE.onRpcComplete();
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
  }

  private StringBuilder url;
  private boolean hasQueryParams;
  private String contentType;
  private String contentData;

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

  public RestApi addParameter(String name, String... value) {
    for (String val : value) {
      addParameter(name, val);
    }
    return this;
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

  public RestApi data(JavaScriptObject obj) {
    return data(new JSONObject(obj));
  }

  public RestApi data(JSONObject obj) {
    contentType = JsonConstants.JSON_REQ_CT;
    contentData = obj.toString();
    return this;
  }

  public RestApi data(String data) {
    contentType = "text/plain; charset=utf-8";
    contentData = data;
    return this;
  }


  public <T extends JavaScriptObject> void get(AsyncCallback<T> cb) {
    send(GET, cb);
  }

  public <T extends JavaScriptObject> void put(AsyncCallback<T> cb) {
    send(PUT, cb);
  }

  public <T extends JavaScriptObject> void delete(AsyncCallback<T> cb) {
    send(DELETE, cb);
  }

  public <T extends JavaScriptObject> void post(AsyncCallback<T> cb) {
    send(POST, cb);
  }

  public <T extends JavaScriptObject> void send(
      Method method,
      final AsyncCallback<T> cb) {
    RequestBuilder req = new RequestBuilder(method, url.toString());
    req.setHeader("Accept", JsonConstants.JSON_TYPE);
    if (Gerrit.getAuthorization() != null) {
      req.setHeader("Authorization", Gerrit.getAuthorization());
    }
    if (contentData != null) {
      req.setHeader("Content-Type", contentType);
    }
    try {
      RpcStatus.INSTANCE.onRpcStart();
      req.sendRequest(contentData, new MyRequestCallback<T>(cb));
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

  private static <T extends JavaScriptObject> T parseJson(Response res)
      throws JSONException {
    String json = res.getText();
    if (json.startsWith(JSON_MAGIC)) {
      json = json.substring(JSON_MAGIC.length());
    }
    if (json.isEmpty()) {
      throw new JSONException("response was empty");
    }
    return cast(JSONParser.parseStrict(json));
  }

  @SuppressWarnings("unchecked")
  private static <T extends JavaScriptObject> T cast(JSONValue val) {
    if (val.isObject() != null) {
      return (T) val.isObject().getJavaScriptObject();
    } else if (val.isArray() != null) {
      return (T) val.isArray().getJavaScriptObject();
    } else if (val.isString() != null) {
      return (T) NativeString.wrap(val.isString().stringValue());
    } else if (val.isNull() != null) {
      return null;
    } else {
      throw new JSONException("unsupported JSON type");
    }
  }

  private static class ErrorMessage extends JavaScriptObject {
    final native String message() /*-{ return this.message; }-*/;

    protected ErrorMessage() {
    }
  }
}
