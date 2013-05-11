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

package com.google.gerrit.server.util;

import com.google.gerrit.common.data.UiCommandDetail;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.webui.UiCommand;
import com.google.gerrit.extensions.webui.UiCommand.Place;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UiCommandUtil {

  public static <R extends RestResource> UiCommandDetail createCommandDetail(
      final String parent, final String name, final String method,
      UiCommand<R> cmd, final R resource) {
    final UiCommandDetail dsc = new UiCommandDetail();
    dsc.id = parent + '~' + name;
    dsc.method = method;
    dsc.label = cmd.getLabel(resource);
    dsc.title = cmd.getTitle(resource);
    dsc.enabled = cmd.isEnabled(resource);
    return dsc;
  }

  public static boolean isCommandForRevisionResource(Place place) {
    return place == Place.CURRENT_PATCHSET_ACTION_PANEL
        || place == Place.PATCHSET_ACTION_PANEL;
  }

  public static  List<UiCommandDetail> sortCommands(List<UiCommandDetail> all) {
    Collections.sort(all, new Comparator<UiCommandDetail>() {
      @Override
      public int compare(UiCommandDetail a, UiCommandDetail b) {
        return a.id.compareTo(b.id);
      }
    });
    return all.isEmpty() ? null : all;
  }

}
