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
 * NB: This code was primarily based on
 * org.eclipse.mylyn.internal.gerrit.core.client.GerritService.java.
 *
 * @author Steffen Pingel
 */

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.httpd.rpc.GerritHttpClient.JsonEntity;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GerritService implements InvocationHandler {
  private final String uri;
  protected GerritHttpClient client;

  public GerritService(GerritHttpClient client, String uri) {
    this.client = client;
    this.uri = uri;
  }

  public static <T extends RemoteJsonService> T create(Class<T> serviceClass,
      GerritHttpClient gerritHttpClient) {
    InvocationHandler handler =
        new GerritService(gerritHttpClient,
            "/gerrit/rpc/" + serviceClass.getSimpleName()); //$NON-NLS-1$
    return serviceClass.cast(Proxy.newProxyInstance(GerritService.class
        .getClassLoader(), new Class<?>[] {serviceClass}, handler));
  }

  public String getServiceUri() {
    return uri;
  }

  public Object invoke(Object proxy, final Method method, Object[] args)
      throws Throwable {
    final JSonSupport json = new JSonSupport();

    // Construct request
    final List<Object> parameters = new ArrayList<Object>(args.length - 1);
    for (int i = 0; i < args.length - 1; i++) {
      parameters.add(args[i]);
    }
    @SuppressWarnings("unchecked")
    AsyncCallback<Object> callback =
        (AsyncCallback<Object>) args[args.length - 1];

    try {

      // Execute request
      String responseMessage =
          client.postJsonRequest(getServiceUri(), new JsonEntity() {
            @Override
            public String getContent() {
              return json.createRequest(client.getId(), client.getXsrfKey(),
                  method.getName(), parameters);
            }
          });

      // The last parameter is a parameterized callback that defines the return
      // type
      Type[] types = method.getGenericParameterTypes();
      final Type resultType =
          ((ParameterizedType) types[types.length - 1])
              .getActualTypeArguments()[0];

      Object result = json.parseResponse(responseMessage, resultType);
      callback.onSuccess(result);
    } catch (Exception e) {
      callback.onFailure(e);
    }
    // All methods are designed to be asynchronous and expected to return void
    return null;
  };
}
