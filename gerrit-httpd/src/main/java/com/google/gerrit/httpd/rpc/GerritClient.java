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
 * org.eclipse.mylyn.internal.gerrit.core.client.GerritClient.
 *
 * @author Mikael Kober
 *
 * @author Thomas Westling
 *
 * @author Steffen Pingel
 */
package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.AccountDashboardInfo;
import com.google.gerrit.common.data.ChangeListService;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;

import java.util.HashMap;
import java.util.Map;

public class GerritClient {

  private abstract class GerritOperation<T> implements AsyncCallback<T> {

    private Throwable exception;

    private T result;

    public abstract void execute();

    public Throwable getException() {
      return exception;
    }

    public T getResult() {
      return result;
    }

    public void onFailure(Throwable exception) {
      this.exception = exception;
    }

    public void onSuccess(T result) {
      setResult(result);
    }

    protected void setResult(T result) {
      this.result = result;
    }
  }

  private final GerritHttpClient client;
  private final Map<Class<? extends RemoteJsonService>, RemoteJsonService> serviceByClass;


  public GerritClient(String urlConnection) {
    this.client = new GerritHttpClient(urlConnection);
    this.serviceByClass =
        new HashMap<Class<? extends RemoteJsonService>, RemoteJsonService>();
  }

  /**
   * Called to get all remote changes associated with the id of the user. This
   * includes all open, closed and reviewable reviews for the user.
   *
   * @throws Exception
   */
  public AccountDashboardInfo queryMyReviews(final String preferredEmail)
      throws Exception {
    AccountDashboardInfo ad =
        execute(new GerritOperation<AccountDashboardInfo>() {
          @Override
          public void execute() {
            getChangeListService().forAccountPreferredEmail(preferredEmail,
                this);
          }
        });
    return ad;
  }

  protected <T> T execute(GerritOperation<T> operation) throws Exception {
    try {
      operation.execute();
      if (operation.getException() instanceof Exception) {
        throw (Exception) operation.getException();
      } else if (operation.getException() != null) {
        Exception e = new Exception();
        e.initCause(operation.getException());
        throw e;
      }
      return operation.getResult();
    } finally {
    }
  }

  private ChangeListService getChangeListService() {
    return getService(ChangeListService.class);
  }

  protected synchronized <T extends RemoteJsonService> T getService(
      Class<T> clazz) {
    RemoteJsonService service = serviceByClass.get(clazz);
    if (service == null) {
      service = GerritService.create(clazz, client);
      serviceByClass.put(clazz, service);
    }
    return clazz.cast(service);
  }
}
