// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.extensions.webui;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.PrivateInternals_UiActionDescription;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityUtils;
import com.google.inject.Provider;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UiActions {
  private static final Logger log = LoggerFactory.getLogger(UiActions.class);

  public static Predicate<UiAction.Description> enabled() {
    return UiAction.Description::isEnabled;
  }

  public static <R extends RestResource> Iterable<UiAction.Description> from(
      RestCollection<?, R> collection, R resource, Provider<CurrentUser> userProvider) {
    return from(collection.views(), resource, userProvider);
  }

  public static <R extends RestResource> Iterable<UiAction.Description> from(
      DynamicMap<RestView<R>> views, final R resource, final Provider<CurrentUser> userProvider) {
    return FluentIterable.from(views)
        .transform(
            (DynamicMap.Entry<RestView<R>> e) -> {
              int d = e.getExportName().indexOf('.');
              if (d < 0) {
                return null;
              }

              RestView<R> view;
              try {
                view = e.getProvider().get();
              } catch (RuntimeException err) {
                log.error(
                    String.format(
                        "error creating view %s.%s", e.getPluginName(), e.getExportName()),
                    err);
                return null;
              }

              if (!(view instanceof UiAction)) {
                return null;
              }

              try {
                CapabilityUtils.checkRequiresCapability(
                    userProvider, e.getPluginName(), view.getClass());
              } catch (AuthException exc) {
                return null;
              }

              UiAction.Description dsc = ((UiAction<R>) view).getDescription(resource);
              if (dsc == null || !dsc.isVisible()) {
                return null;
              }

              String name = e.getExportName().substring(d + 1);
              PrivateInternals_UiActionDescription.setMethod(
                  dsc, e.getExportName().substring(0, d));
              PrivateInternals_UiActionDescription.setId(
                  dsc, "gerrit".equals(e.getPluginName()) ? name : e.getPluginName() + '~' + name);
              return dsc;
            })
        .filter(Objects::nonNull);
  }

  private UiActions() {}
}
