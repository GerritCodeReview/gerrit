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
 * org.eclipse.mylyn.internal.gerrit.core.client.GerritHttpClient.java.
 *
 * @author Daniel Olsson, ST Ericsson
 *
 * @author Thomas Westling
 *
 * @author Steffen Pingel
 */

package com.google.gerrit.httpd.rpc;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class GerritHttpClient {

  public static abstract class JsonEntity {

    public abstract String getContent();

  }

  private int id = 1;
  private volatile Cookie xsrfCookie;
  private final String urlConnection;

  private static final int CONNECT_TIMEOUT = 60 * 1000;

  public GerritHttpClient(final String urlConnection) {
    this.urlConnection = urlConnection;
  }

  public synchronized int getId() {
    return id++;
  }

  public synchronized String getXsrfKey() {
    return (xsrfCookie != null) ? xsrfCookie.getValue() : null;
  }

  public String postJsonRequest(String serviceUri, JsonEntity entity)
      throws IOException, Exception {

    final PostMethod method = postJsonRequestInternal(serviceUri, entity);

    int code = method.getStatusCode();
    if (code == HttpURLConnection.HTTP_OK) {
      return method.getResponseBodyAsString();
    } else {
      method.releaseConnection();
      throw new Exception();
    }
  }

  private PostMethod postJsonRequestInternal(String serviceUri,
      JsonEntity entity) throws IOException {
    final HttpClient httpClient = new HttpClient(getConnectionManager());
    final URL url = new URL(urlConnection);

    final HostConfiguration hConfig = new HostConfiguration();
    hConfig.setHost(url.getHost(), url.getPort(), url.getProtocol());

    final PostMethod method = new PostMethod(url + serviceUri);
    method.setRequestHeader("Content-Type", "application/json; charset=utf-8"); //$NON-NLS-1$//$NON-NLS-2$
    method.setRequestHeader("Accept", "application/json"); //$NON-NLS-1$//$NON-NLS-2$

    try {
      final RequestEntity requestEntity =
          new StringRequestEntity(entity.getContent(), "application/json", null); //$NON-NLS-1$.
      method.setRequestEntity(requestEntity);

      httpClient.executeMethod(hConfig, method, null);
      return method;
    } catch (IOException e) {
      method.releaseConnection();
      throw e;
    }
  }

  private HttpConnectionManager getConnectionManager() {
    final HttpConnectionManager connectionManager =
        new MultiThreadedHttpConnectionManager();
    final HttpConnectionManagerParams connectionParams =
        connectionManager.getParams();

    connectionParams.setConnectionTimeout(CONNECT_TIMEOUT);
    return connectionManager;
  }
}
