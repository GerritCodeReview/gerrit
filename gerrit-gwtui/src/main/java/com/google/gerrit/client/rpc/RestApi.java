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
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.StatusCodeException;

/** Makes a REST API call to the server. */
public class RestApi {
  private static final int SC_UNAVAILABLE = 2;
  private static final int SC_TRANSPORT = 3;
  private static final String JSON_TYPE = "application/json";
  private static final String TEXT_TYPE = "text/plain";

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

  /** True if err is a StatusCodeException reporting Not Found. */
  public static boolean isNotFound(Throwable err) {
    return isStatus(err, Response.SC_NOT_FOUND);
  }

  /** True if err is describing a user that is currently anonymous. */
  public static boolean isNotSignedIn(Throwable err) {
    if (err instanceof StatusCodeException) {
      StatusCodeException sce = (StatusCodeException) err;
      if (sce.getStatusCode() == Response.SC_UNAUTHORIZED) {
        return true;
      }
      return sce.getStatusCode() == Response.SC_FORBIDDEN
          && (sce.getEncodedResponse().equals("Authentication required")
              || sce.getEncodedResponse().startsWith("Must be signed-in"));
    }
    return false;
  }

  /** True if err is a StatusCodeException with a specific HTTP code. */
  public static boolean isStatus(Throwable err, int status) {
    return err instanceof StatusCodeException
        && ((StatusCodeException) err).getStatusCode() == status;
  }

  /** Is the Gerrit Code Review server likely to return this status? */
  public static boolean isExpected(int statusCode) {
    switch (statusCode) {
      case SC_UNAVAILABLE:
      case 400: // Bad Request
      case 401: // Unauthorized
      case 403: // Forbidden
      case 404: // Not Found
      case 405: // Method Not Allowed
      case 409: // Conflict
      case 429: // Too Many Requests (RFC 6585)
        return true;

      default:
        // Assume any other code is not expected. These may be
        // local proxy server errors outside of our control.
        return false;
    }
  }

  private static class MyRequestCallback<T extends JavaScriptObject>
      implements RequestCallback {
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
          JSONValue v;
          try {
            v = parseJson(res);
          } catch (JSONException e) {
            v = null;
          }
          if (v != null && v.isString() != null) {
            msg = v.isString().stringValue();
          } else {
            msg = trimJsonMagic(res.getText()).trim();
          }
        } else {
          msg = res.getStatusText();
        }

        RpcStatus.INSTANCE.onRpcComplete();
        cb.onFailure(new StatusCodeException(status, msg));
        return;
      }

      if (!isJsonBody(res)) {
        RpcStatus.INSTANCE.onRpcComplete();
        cb.onFailure(new StatusCodeException(200, "Expected "
            + JSON_TYPE + "; received Content-Type: "
            + res.getHeader("Content-Type")));
        return;
      }

      T data;
      try {
        data = cast(parseJson(res));
      } catch (JSONException e) {
        RpcStatus.INSTANCE.onRpcComplete();
        cb.onFailure(new StatusCodeException(200,
            "Invalid JSON: " + e.getMessage()));
        return;
      }

      cb.onSuccess(data);
      RpcStatus.INSTANCE.onRpcComplete();
    }

    @Override
    public void onError(Request req, Throwable err) {
      RpcStatus.INSTANCE.onRpcComplete();
      if (err.getMessage().contains("XmlHttpRequest.status")) {
        cb.onFailure(new StatusCodeException(
            SC_UNAVAILABLE,
            RpcConstants.C.errorServerUnavailable()));
      } else {
        cb.onFailure(new StatusCodeException(SC_TRANSPORT, err.getMessage()));
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
    contentType = JSON_TYPE + "; charset=utf-8";
    contentData = obj.toString();
    return this;
  }

  public RestApi data(String data) {
    contentType = TEXT_TYPE + "; charset=utf-8";
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
    req.setHeader("Accept", JSON_TYPE);
    if (Gerrit.getAuthorization() != null) {
      req.setHeader("Authorization", Gerrit.getAuthorization());
    }
    if (contentData != null) {
      req.setHeader("Content-Type", contentType);
    }
    MyRequestCallback<T> httpCallback = new MyRequestCallback<T>(cb);
    try {
      RpcStatus.INSTANCE.onRpcStart();
      req.sendRequest(contentData, httpCallback);
    } catch (RequestException e) {
      httpCallback.onError(null, e);
    }
  }

  private static boolean isJsonBody(Response res) {
    return isContentType(res, JSON_TYPE);
  }

  private static boolean isTextBody(Response res) {
    return isContentType(res, TEXT_TYPE);
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

  private static JSONValue parseJson(Response res)
      throws JSONException {
    String json = trimJsonMagic(res.getText());
    if (json.isEmpty()) {
      throw new JSONException("response was empty");
    }
    return JSONParser.parseStrict(json);
  }

  private static String trimJsonMagic(String json) {
    if (json.startsWith(JSON_MAGIC)) {
      json = json.substring(JSON_MAGIC.length());
    }
    return json;
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
}
