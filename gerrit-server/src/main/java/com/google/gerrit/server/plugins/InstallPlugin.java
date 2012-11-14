// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PutInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.plugins.InstallPlugin.Input;
import com.google.inject.Inject;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

class InstallPlugin implements RestModifyView<TopLevelResource, Input> {
  static class Input {
    PutInput raw;
  }

  private final PluginLoader loader;
  private final String name;

  InstallPlugin(PluginLoader loader, String name) {
    this.loader = loader;
    this.name = name;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(TopLevelResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    try {
      InputStream in = input.raw.getInputStream();
      try {
        loader.installPluginFromStream(name, in);
      } finally {
        in.close();
      }
    } catch (PluginInstallException e) {
      StringWriter buf = new StringWriter();
      buf.write(String.format("cannot install %s\n", name));
      PrintWriter pw = new PrintWriter(buf);
      e.printStackTrace(pw);
      pw.flush();
      throw new BadRequestException(buf.toString());
    }
    return new ListPlugins.PluginInfo(loader.get(name));
  }

  static class Overwrite implements RestModifyView<PluginResource, Input> {
    private final PluginLoader loader;

    @Inject
    Overwrite(PluginLoader loader) {
      this.loader = loader;
    }

    @Override
    public Class<Input> inputType() {
      return Input.class;
    }

    @Override
    public Object apply(PluginResource resource, Input input)
        throws AuthException, BadRequestException, ResourceConflictException,
        Exception {
      return new InstallPlugin(loader, resource.getName())
        .apply(TopLevelResource.INSTANCE, input);
    }
  }
}
