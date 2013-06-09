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

  private Map<String, String> all;

  PermissionNameRenderer(Map<String, String> allFromOutside) {
    all = new HashMap<String, String>();
    all.putAll(allFromOutside);
    for (Map.Entry<String, String> e : Util.C.permissionNames().entrySet()) {
      all.put(e.getKey(), e.getValue());
      all.put(e.getKey().toLowerCase(), e.getValue());
    }
  }

  @Override
  public String render(String varName) {
    if (Permission.isLabel(varName)) {
      return Util.M.label(new Permission(varName).getLabel());
    }

    String desc = all.get(varName);
    if (desc == null) {
      desc = all.get(varName.toLowerCase());
    }
    return desc != null ? desc : varName;
  }

  @Override
  public void render(String object, Appendable appendable) throws IOException {
    appendable.append(render(object));
  }
}