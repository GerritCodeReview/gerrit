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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UiActions {
  private static final Logger log = LoggerFactory.getLogger(UiActions.class);

  public static Predicate<UiAction.Description> enabled() {
    return new Predicate<UiAction.Description>() {
      @Override
      public boolean apply(UiAction.Description input) {
        return input.isEnabled();
      }
    };
  }

  public static List<UiAction.Description> sorted(Iterable<UiAction.Description> in) {
    List<UiAction.Description> s = Lists.newArrayList(in);
    Collections.sort(s, new Comparator<UiAction.Description>() {
      @Override
      public int compare(UiAction.Description a, UiAction.Description b) {
        return a.getId().compareTo(b.getId());
      }
    });
    return s;
  }

  public static Iterable<UiAction.Description> plugins(Iterable<UiAction.Description> in) {
    return Iterables.filter(in,
      new Predicate<UiAction.Description>() {
        @Override
        public boolean apply(UiAction.Description input) {
          return input.getId().indexOf('~') > 0;
        }
      });
  }

  public static <R extends RestResource> Iterable<UiAction.Description> from(
      RestCollection<?, R> collection,
      R resource,
      Provider<CurrentUser> userProvider) {
    return from(collection.views(), resource, userProvider);
  }

  public static <R extends RestResource> Iterable<UiAction.Description> from(
      DynamicMap<RestView<R>> views,
      final R resource,
      final Provider<CurrentUser> userProvider) {
    return Iterables.filter(
      Iterables.transform(
        views,
        new Function<DynamicMap.Entry<RestView<R>>, UiAction.Description> () {
          @Override
          @Nullable
          public UiAction.Description apply(DynamicMap.Entry<RestView<R>> e) {
            int d = e.getExportName().indexOf('.');
            if (d < 0) {
              return null;
            }

            RestView<R> view;
            try {
              view = e.getProvider().get();
            } catch (RuntimeException err) {
              log.error(String.format(
                  "error creating view %s.%s",
                  e.getPluginName(), e.getExportName()), err);
              return null;
            }

            if (!(view instanceof UiAction)) {
              return null;
            }

            try {
              CapabilityUtils.checkRequiresCapability(userProvider,
                  e.getPluginName(), view.getClass());
            } catch (AuthException exc) {
              return null;
            }

            UiAction.Description dsc =
                ((UiAction<R>) view).getDescription(resource);
            if (dsc == null || !dsc.isVisible()) {
              return null;
            }

            String name = e.getExportName().substring(d + 1);
            PrivateInternals_UiActionDescription.setMethod(
                dsc,
                e.getExportName().substring(0, d));
            PrivateInternals_UiActionDescription.setId(
                dsc,
                "gerrit".equals(e.getPluginName())
                  ? name
                  : e.getPluginName() + '~' + name);
            return dsc;
          }
        }),
      Predicates.notNull());
  }

  private UiActions() {
  }
}
