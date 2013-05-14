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
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.UiCommandDetail;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

public class UiCommands {
  private static final Logger log = LoggerFactory.getLogger(UiCommands.class);

  public static Predicate<UiCommandDetail> enabled() {
    return new Predicate<UiCommandDetail>() {
      @Override
      public boolean apply(UiCommandDetail input) {
        return input.enabled;
      }
    };
  }

  public static List<UiCommandDetail> sorted(Iterable<UiCommandDetail> in) {
    List<UiCommandDetail> s = Lists.newArrayList(in);
    Collections.sort(s, new Comparator<UiCommandDetail>() {
      @Override
      public int compare(UiCommandDetail a, UiCommandDetail b) {
        return a.id.compareTo(b.id);
      }
    });
    return s;
  }

  public static List<UiCommandDetail> sorted(List<UiCommandDetail> c) {
    Collections.sort(c, new Comparator<UiCommandDetail>() {
      @Override
      public int compare(UiCommandDetail a, UiCommandDetail b) {
        return a.id.compareTo(b.id);
      }
    });
    return c;
  }

  public static <R extends RestResource> Iterable<UiCommandDetail> from(
      ChildCollection<?, R> collection,
      R resource,
      EnumSet<UiCommand.Place> places) {
    return from(collection.views(), resource, places);
  }

  public static <R extends RestResource> Iterable<UiCommandDetail> from(
      DynamicMap<RestView<R>> views,
      final R resource,
      final EnumSet<UiCommand.Place> places) {
    return Iterables.filter(
      Iterables.transform(
        views,
        new Function<DynamicMap.Entry<RestView<R>>, UiCommandDetail> () {
          @Override
          @Nullable
          public UiCommandDetail apply(DynamicMap.Entry<RestView<R>> e) {
            int d = e.getExportName().indexOf('.');
            if (d < 0) {
              return null;
            }

            String method = e.getExportName().substring(0, d);
            String name = e.getExportName().substring(d + 1);
            RestView<R> view;
            try {
              view = e.getProvider().get();
            } catch (RuntimeException err) {
              log.error(String.format(
                  "error creating view %s.%s",
                  e.getPluginName(), e.getExportName()), err);
              return null;
            }

            if (!(view instanceof UiCommand)) {
              return null;
            }

            UiCommand<R> cmd = (UiCommand<R>) view;
            if (Sets.intersection(cmd.getPlaces(), places).isEmpty()
                || !cmd.isVisible(resource)) {
              return null;
            }

            UiCommandDetail dsc = new UiCommandDetail();
            dsc.id = e.getPluginName() + '~' + name;
            dsc.method = method;
            dsc.label = cmd.getLabel(resource);
            dsc.title = cmd.getTitle(resource);
            dsc.enabled = cmd.isEnabled(resource);
            return dsc;
          }
        }),
      Predicates.notNull());
  }

  private UiCommands() {
  }
}
