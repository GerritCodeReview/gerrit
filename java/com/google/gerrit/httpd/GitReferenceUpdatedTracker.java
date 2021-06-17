// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Stores the updated refs whenever they are updated, so that we can export this information in the
 * response headers.
 *
 * <p>This is only working for HTTP requests. {@link WebSession} is not bound outside of HTTP
 * requests.
 */
@Singleton
public class GitReferenceUpdatedTracker implements GitReferenceUpdatedListener {

  private final DynamicItem<WebSession> webSession;

  @Inject
  GitReferenceUpdatedTracker(DynamicItem<WebSession> webSession) {
    this.webSession = webSession;
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    WebSession currentSession = null;
    try {
      currentSession = webSession.get();
    } catch (Exception ex) {
      // We couldn't bind the current session properly. This is expected to happen at any point we
      // perform ref updates without an HTTP request (git push for example).
      // If we can't get a WebSession, we don't need to track the updated references.
      return;
    }
    if (currentSession != null) {
      currentSession.addRefUpdatedEvents(event);
    }
  }
}
