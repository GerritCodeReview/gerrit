// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.account.CapabilityUtils.checkRequiresCapability;

import com.google.common.base.Strings;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.extensions.webui.HasETag;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Singleton
public class GetRevisionActions implements ETagView<RevisionResource> {
  private static final Logger log =
      LoggerFactory.getLogger(GetRevisionActions.class);

  private final Revisions revisions;
  private final ActionJson json;
  private final Provider<CurrentUser> user;

  @Inject
  GetRevisionActions(Revisions revisions, ActionJson json,
      Provider<CurrentUser> user) {
    this.revisions = revisions;
    this.json = json;
    this.user = user;
  }

  @Override
  public Response<Map<String, ActionInfo>> apply(RevisionResource rsrc) {
    return Response.withMustRevalidate(json.format(rsrc));
  }

  @Override
  public String getETag(RevisionResource rsrc) {
    Hasher h = Hashing.md5().newHasher();
    for (DynamicMap.Entry<RestView<RevisionResource>> e : revisions.views()) {
      buildETag(h, e, rsrc);
    }
    return h.hash().toString();
  }

  private void buildETag(Hasher h,
      DynamicMap.Entry<RestView<RevisionResource>> e,
      RevisionResource rsrc) {
    RestView<RevisionResource> view;
    try {
      view = e.getProvider().get();
    } catch (RuntimeException err) {
      log.error(String.format(
          "error creating view %s.%s",
          e.getPluginName(), e.getExportName()), err);
      return;
    }

    if (!(view instanceof UiAction)) {
      return;
    }

    try {
      checkRequiresCapability(user, e.getPluginName(), view.getClass());
    } catch (AuthException notAllowed) {
      return;
    }

    if (view instanceof HasETag) {
      h.putUnencodedChars(e.getPluginName());
      h.putUnencodedChars(e.getExportName());
      ((HasETag<RevisionResource>) view).buildETag(h, rsrc);

    } else {
      UiAction.Description d =
          ((UiAction<RevisionResource>) view).getDescription(rsrc);

      h.putUnencodedChars(d.getMethod())
       .putUnencodedChars(d.getId())
       .putUnencodedChars(Strings.nullToEmpty(d.getTitle()))
       .putUnencodedChars(Strings.nullToEmpty(d.getLabel()))
       .putBoolean(d.isVisible())
       .putBoolean(d.isEnabled());
    }
  }
}
