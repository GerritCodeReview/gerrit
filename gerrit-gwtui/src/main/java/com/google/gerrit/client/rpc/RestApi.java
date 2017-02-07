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
import com.google.gerrit.common.data.HostPageData;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestBuilder.Method;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONException;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.StatusCodeException;

/** Makes a REST API call to the server. */
public class RestApi {
  private static final int SC_UNAVAILABLE = 2;
  private static final int SC_BAD_TRANSPORT = 3;
  private static final int SC_BAD_RESPONSE = 4;
  private static final String JSON_TYPE = "application/json";
  private static final String JSON_UTF8 = JSON_TYPE + "; charset=utf-8";
  private static final String TEXT_TYPE = "text/plain";
  private static final String TEXT_UTF8 = TEXT_TYPE + "; charset=utf-8";

  /**
   * Expected JSON content body prefix that prevents XSSI.
   *
   * <p>The server always includes this line as the first line of the response content body when the
   * response body is formatted as JSON. It gets inserted by the server to prevent the resource from
   * being imported into another domain's page using a &lt;script&gt; tag. This line must be removed
   * before the JSON can be parsed.
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
              || sce.getEncodedResponse().startsWith("Must be signed-in")
              || sce.getEncodedResponse().startsWith("Invalid authentication"));
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
      case Response.SC_BAD_REQUEST:
      case Response.SC_UNAUTHORIZED:
      case Response.SC_FORBIDDEN:
      case Response.SC_NOT_FOUND:
      case Response.SC_METHOD_NOT_ALLOWED:
      case Response.SC_CONFLICT:
      case Response.SC_PRECONDITION_FAILED:
      case 422: // Unprocessable Entity
      case 429: // Too Many Requests (RFC 6585)
        return true;

      default:
        // Assume any other code is not expected. These may be
        // local proxy server errors outside of our control.
        return false;
    }
  }

  private static class HttpImpl<T extends JavaScriptObject> implements RequestCallback {
    private final boolean background;
    private final HttpCallback<T> cb;

    HttpImpl(boolean bg, HttpCallback<T> cb) {
      this.background = bg;
      this.cb = cb;
    }

    @Override
    public void onResponseReceived(Request req, final Response res) {
      int status = res.getStatusCode();
      if (status == Response.SC_NO_CONTENT) {
        cb.onSuccess(new HttpResponse<T>(res, null, null));
        if (!background) {
          RpcStatus.INSTANCE.onRpcComplete();
        }

      } else if (200 <= status && status < 300) {
        long start = System.currentTimeMillis();
        final T data;
        final String type;
        if (isJsonBody(res)) {
          try {
            JSONValue val = parseJson(res);
            if (isJsonEncoded(res) && val.isString() != null) {
              data = NativeString.wrap(val.isString().stringValue()).cast();
              type = simpleType(res.getHeader("X-FYI-Content-Type"));
            } else {
              data = RestApi.<T>cast(val);
              type = JSON_TYPE;
            }
          } catch (JSONException e) {
            if (!background) {
              RpcStatus.INSTANCE.onRpcComplete();
            }
            cb.onFailure(
                new StatusCodeException(SC_BAD_RESPONSE, "Invalid JSON: " + e.getMessage()));
            return;
          }
        } else if (isTextBody(res)) {
          data = NativeString.wrap(res.getText()).cast();
          type = TEXT_TYPE;
        } else {
          if (!background) {
            RpcStatus.INSTANCE.onRpcComplete();
          }
          cb.onFailure(
              new StatusCodeException(
                  SC_BAD_RESPONSE,
                  "Expected "
                      + JSON_TYPE
                      + " or "
                      + TEXT_TYPE
                      + "; received Content-Type: "
                      + res.getHeader("Content-Type")));
          return;
        }

        Scheduler.ScheduledCommand cmd =
            new Scheduler.ScheduledCommand() {
              @Override
              public void execute() {
                try {
                  cb.onSuccess(new HttpResponse<>(res, type, data));
                } finally {
                  if (!background) {
                    RpcStatus.INSTANCE.onRpcComplete();
                  }
                }
              }
            };

        // Defer handling the response if the parse took a while.
        if ((System.currentTimeMillis() - start) > 75) {
          Scheduler.get().scheduleDeferred(cmd);
        } else {
          cmd.execute();
        }
      } else {
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

        if (!background) {
          RpcStatus.INSTANCE.onRpcComplete();
        }
        cb.onFailure(new StatusCodeException(status, msg));
      }
    }

    @Override
    public void onError(Request req, Throwable err) {
      if (!background) {
        RpcStatus.INSTANCE.onRpcComplete();
      }
      if (err.getMessage().contains("XmlHttpRequest.status")) {
        cb.onFailure(
            new StatusCodeException(SC_UNAVAILABLE, RpcConstants.C.errorServerUnavailable()));
      } else {
        cb.onFailure(new StatusCodeException(SC_BAD_TRANSPORT, err.getMessage()));
      }
    }
  }

  private StringBuilder url;
  private boolean hasQueryParams;
  private boolean background;
  private String ifNoneMatch;

  /**
   * Initialize a new API call.
   *
   * <p>By default the JSON format will be selected by including an HTTP Accept header in the
   * request.
   *
   * @param name URL of the REST resource to access, e.g. {@code "/projects/"} to list accessible
   *     projects from the server.
   */
  public RestApi(String name) {
    if (name.startsWith("/")) {
      name = name.substring(1);
    }

    url = new StringBuilder();
    url.append(GWT.getHostPageBaseURL());
    url.append(name);
  }

  public RestApi view(String name) {
    return idRaw(name);
  }

  public RestApi id(String id) {
    return idRaw(URL.encodePathSegment(id));
  }

  public RestApi id(int id) {
    return idRaw(Integer.toString(id));
  }

  public RestApi idRaw(String name) {
    if (hasQueryParams) {
      throw new IllegalStateException();
    }
    if (url.charAt(url.length() - 1) != '/') {
      url.append('/');
    }
    url.append(name);
    return this;
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

  public RestApi ifNoneMatch() {
    return ifNoneMatch("*");
  }

  public RestApi ifNoneMatch(String etag) {
    ifNoneMatch = etag;
    return this;
  }

  public RestApi background() {
    background = true;
    return this;
  }

  public String url() {
    return url.toString();
  }

  public <T extends JavaScriptObject> void get(AsyncCallback<T> cb) {
    get(wrap(cb));
  }

  public <T extends JavaScriptObject> void get(HttpCallback<T> cb) {
    send(GET, cb);
  }

  public <T extends JavaScriptObject> void delete(AsyncCallback<T> cb) {
    delete(wrap(cb));
  }

  public <T extends JavaScriptObject> void delete(HttpCallback<T> cb) {
    send(DELETE, cb);
  }

  private <T extends JavaScriptObject> void send(Method method, HttpCallback<T> cb) {
    HttpImpl<T> httpCallback = new HttpImpl<>(background, cb);
    try {
      if (!background) {
        RpcStatus.INSTANCE.onRpcStart();
      }
      request(method).sendRequest(null, httpCallback);
    } catch (RequestException e) {
      httpCallback.onError(null, e);
    }
  }

  public <T extends JavaScriptObject> void post(JavaScriptObject content, AsyncCallback<T> cb) {
    post(content, wrap(cb));
  }

  public <T extends JavaScriptObject> void post(JavaScriptObject content, HttpCallback<T> cb) {
    sendJSON(POST, content, cb);
  }

  public <T extends JavaScriptObject> void post(String content, AsyncCallback<T> cb) {
    post(content, wrap(cb));
  }

  public <T extends JavaScriptObject> void post(String content, HttpCallback<T> cb) {
    sendText(POST, content, cb);
  }

  public <T extends JavaScriptObject> void put(AsyncCallback<T> cb) {
    put(wrap(cb));
  }

  public <T extends JavaScriptObject> void put(HttpCallback<T> cb) {
    send(PUT, cb);
  }

  public <T extends JavaScriptObject> void put(String content, AsyncCallback<T> cb) {
    put(content, wrap(cb));
  }

  public <T extends JavaScriptObject> void put(String content, HttpCallback<T> cb) {
    sendText(PUT, content, cb);
  }

  public <T extends JavaScriptObject> void put(JavaScriptObject content, AsyncCallback<T> cb) {
    put(content, wrap(cb));
  }

  public <T extends JavaScriptObject> void put(JavaScriptObject content, HttpCallback<T> cb) {
    sendJSON(PUT, content, cb);
  }

  private <T extends JavaScriptObject> void sendJSON(
      Method method, JavaScriptObject content, HttpCallback<T> cb) {
    HttpImpl<T> httpCallback = new HttpImpl<>(background, cb);
    try {
      if (!background) {
        RpcStatus.INSTANCE.onRpcStart();
      }
      RequestBuilder req = request(method);
      req.setHeader("Content-Type", JSON_UTF8);
      req.sendRequest(str(content), httpCallback);
    } catch (RequestException e) {
      httpCallback.onError(null, e);
    }
  }

  private static native String str(JavaScriptObject jso) /*-{ return JSON.stringify(jso) }-*/;

  private <T extends JavaScriptObject> void sendText(
      Method method, String body, HttpCallback<T> cb) {
    HttpImpl<T> httpCallback = new HttpImpl<>(background, cb);
    try {
      if (!background) {
        RpcStatus.INSTANCE.onRpcStart();
      }
      RequestBuilder req = request(method);
      req.setHeader("Content-Type", TEXT_UTF8);
      req.sendRequest(body, httpCallback);
    } catch (RequestException e) {
      httpCallback.onError(null, e);
    }
  }

  private RequestBuilder request(Method method) {
    RequestBuilder req = new RequestBuilder(method, url());
    if (ifNoneMatch != null) {
      req.setHeader("If-None-Match", ifNoneMatch);
    }
    req.setHeader("Accept", JSON_TYPE);
    if (Gerrit.getXGerritAuth() != null) {
      req.setHeader(HostPageData.XSRF_HEADER_NAME, Gerrit.getXGerritAuth());
    }
    return req;
  }

  private static boolean isJsonBody(Response res) {
    return isContentType(res, JSON_TYPE);
  }

  private static boolean isTextBody(Response res) {
    return isContentType(res, TEXT_TYPE);
  }

  private static boolean isJsonEncoded(Response res) {
    return "json".equals(res.getHeader("X-FYI-Content-Encoding"));
  }

  private static boolean isContentType(Response res, String want) {
    String type = res.getHeader("Content-Type");
    return type != null && want.equals(simpleType(type));
  }

  private static String simpleType(String type) {
    int semi = type.indexOf(';');
    if (semi >= 0) {
      return type.substring(0, semi).trim();
    }
    return type;
  }

  private static JSONValue parseJson(Response res) throws JSONException {
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

  private static <T extends JavaScriptObject> HttpCallback<T> wrap(final AsyncCallback<T> cb) {
    return new HttpCallback<T>() {
      @Override
      public void onSuccess(HttpResponse<T> r) {
        cb.onSuccess(r.getResult());
      }

      @Override
      public void onFailure(Throwable e) {
        cb.onFailure(e);
      }
    };
  }
}
