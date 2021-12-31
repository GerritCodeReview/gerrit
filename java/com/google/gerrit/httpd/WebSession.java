// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AuthResult;
import com.google.inject.jakarta.RequestScoped;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A thread safe class that contains details about a specific user web session.
 *
 * <p>WARNING: All implementors must have {@link RequestScoped} annotation to maintain thread
 * safety.
 */
public abstract class WebSession {
  public abstract boolean isSignedIn();

  @Nullable
  public abstract String getXGerritAuth();

  public abstract boolean isValidXGerritAuth(String keyIn);

  public abstract CurrentUser getUser();

  public abstract void login(AuthResult res, boolean rememberMe);

  /** Set the user account for this current request only. */
  public abstract void setUserAccountId(Account.Id id);

  public abstract boolean isAccessPathOk(AccessPath path);

  public abstract void setAccessPathOk(AccessPath path, boolean ok);

  public abstract void logout();

  public abstract String getSessionId();

  /**
   * Store and return the ref updates in this session. This class is {@link RequestScoped}, hence
   * this is thread safe.
   *
   * <p>The same session could perform separate requests one after another, so resetting the ref
   * updates is necessary between requests.
   */
  private List<GitReferenceUpdatedListener.Event> refUpdatedEvents = new CopyOnWriteArrayList<>();

  public List<GitReferenceUpdatedListener.Event> getRefUpdatedEvents() {
    return refUpdatedEvents;
  }

  public void addRefUpdatedEvents(GitReferenceUpdatedListener.Event event) {
    refUpdatedEvents.add(event);
  }

  public void resetRefUpdatedEvents() {
    refUpdatedEvents.clear();
  }
}
