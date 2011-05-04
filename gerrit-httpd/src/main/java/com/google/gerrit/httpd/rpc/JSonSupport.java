// Copyright (C) 2011 The Android Open Source Project
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

/*
 * NB: This code was primarily ripped out of
 * org.eclipse.mylyn.internal.gerrit.core.client.JSonSupport.java.
 *
 * @author Steffen Pingel
 */

package com.google.gerrit.httpd.rpc;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.diff.Edit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gwtjsonrpc.server.JsonServlet;

public class JSonSupport {

  /**
   * Parses a Json response.
   */
  private class JSonResponseDeserializer implements
      JsonDeserializer<JSonResponse> {
    public JSonResponse deserialize(JsonElement json, Type typeOfT,
        JsonDeserializationContext context) throws JsonParseException {
      JsonObject object = json.getAsJsonObject();
      JSonResponse response = new JSonResponse();
      response.jsonrpc = object.get("jsonrpc").getAsString(); //$NON-NLS-1$
      response.id = object.get("id").getAsInt(); //$NON-NLS-1$
      response.result = object.get("result"); //$NON-NLS-1$
      response.error = object.get("error"); //$NON-NLS-1$
      return response;
    }
  }

  static class JSonError {
    int code;

    String message;
  }

  static class JsonRequest {
    int id;

    final String jsonrpc = "2.0"; //$NON-NLS-1$

    String method;

    final List<Object> params = new ArrayList<Object>();

    String xsrfKey;
  }

  static class JSonResponse {
    JsonElement error;

    int id;

    String jsonrpc;

    JsonElement result;
  }

  private Gson gson;

  public JSonSupport() {
    gson =
        JsonServlet.defaultGsonBuilder().registerTypeAdapter(
            JSonResponse.class, new JSonResponseDeserializer())
            .registerTypeAdapter(Edit.class, new JsonDeserializer<Edit>() {
              public Edit deserialize(JsonElement json, Type typeOfT,
                  JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonArray()) {
                  JsonArray array = json.getAsJsonArray();
                  if (array.size() == 4) {
                    return new Edit(array.get(0).getAsInt(), array.get(1)
                        .getAsInt(), array.get(2).getAsInt(), array.get(3)
                        .getAsInt());
                  }
                }
                return new Edit(0, 0);
              }
            }).create();
  }

  public String createRequest(int id, String xsrfKey, String methodName,
      Collection<Object> args) {
    JsonRequest msg = new JsonRequest();
    msg.method = methodName;
    if (args != null) {
      for (Object arg : args) {
        msg.params.add(arg);
      }
    }
    msg.id = id;
    msg.xsrfKey = xsrfKey;
    return gson.toJson(msg, msg.getClass());
  }

  @SuppressWarnings("unchecked")
  public <T> T parseResponse(String responseMessage, Type resultType)
      throws Exception {
    JSonResponse response = gson.fromJson(responseMessage, JSonResponse.class);
    if (response.error != null) {
      JSonError error = gson.fromJson(response.error, JSonError.class);
      throw new Exception(error.message);
    } else {
      return (T) gson.fromJson(response.result, resultType);
    }
  }
}
