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

package com.google.gerrit.client.admin;

import com.google.gerrit.common.data.Permission;
import com.google.gwt.text.shared.Renderer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class PermissionNameRenderer implements Renderer<String> {
  private static final Map<String, String> permissions;

  static {
    permissions = new HashMap<>();
    for (Map.Entry<String, String> e : Util.C.permissionNames().entrySet()) {
      permissions.put(e.getKey(), e.getValue());
      permissions.put(e.getKey().toLowerCase(), e.getValue());
    }
  }

  private final Map<String, String> fromServer;

  PermissionNameRenderer(Map<String, String> allFromOutside) {
    fromServer = allFromOutside;
  }

  @Override
  public String render(String varName) {
    if (Permission.isLabelAs(varName)) {
      return Util.M.labelAs(Permission.extractLabel(varName));
    } else if (Permission.isLabel(varName)) {
      return Util.M.label(Permission.extractLabel(varName));
    }

    String desc = permissions.get(varName);
    if (desc != null) {
      return desc;
    }

    desc = fromServer.get(varName);
    if (desc != null) {
      return desc;
    }

    desc = permissions.get(varName.toLowerCase());
    if (desc != null) {
      return desc;
    }

    desc = fromServer.get(varName.toLowerCase());
    if (desc != null) {
      return desc;
    }
    return varName;
  }

  @Override
  public void render(String object, Appendable appendable) throws IOException {
    appendable.append(render(object));
  }
}
