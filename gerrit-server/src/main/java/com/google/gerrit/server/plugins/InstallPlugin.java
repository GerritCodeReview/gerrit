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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.plugins.InstallPlugin.Input;
import com.google.gerrit.server.plugins.ListPlugins.PluginInfo;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.zip.ZipException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
class InstallPlugin implements RestModifyView<TopLevelResource, Input> {
  static class Input {
    @DefaultInput String url;
    RawInput raw;
  }

  private final PluginLoader loader;
  private final String name;
  private final boolean created;

  InstallPlugin(PluginLoader loader, String name, boolean created) {
    this.loader = loader;
    this.name = name;
    this.created = created;
  }

  @Override
  public Response<PluginInfo> apply(TopLevelResource resource, Input input)
      throws BadRequestException, MethodNotAllowedException, IOException {
    if (!loader.isRemoteAdminEnabled()) {
      throw new MethodNotAllowedException("remote installation is disabled");
    }
    try {
      try (InputStream in = openStream(input)) {
        String pluginName = loader.installPluginFromStream(name, in);
        ListPlugins.PluginInfo info = new ListPlugins.PluginInfo(loader.get(pluginName));
        return created ? Response.created(info) : Response.ok(info);
      }
    } catch (PluginInstallException e) {
      StringWriter buf = new StringWriter();
      buf.write(String.format("cannot install %s", name));
      if (e.getCause() instanceof ZipException) {
        buf.write(": ");
        buf.write(e.getCause().getMessage());
      } else {
        buf.write(":\n");
        PrintWriter pw = new PrintWriter(buf);
        e.printStackTrace(pw);
        pw.flush();
      }
      throw new BadRequestException(buf.toString());
    }
  }

  private InputStream openStream(Input input) throws IOException, BadRequestException {
    if (input.raw != null) {
      return input.raw.getInputStream();
    }
    try {
      return new URL(input.url).openStream();
    } catch (IOException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
  static class Overwrite implements RestModifyView<PluginResource, Input> {
    private final PluginLoader loader;

    @Inject
    Overwrite(PluginLoader loader) {
      this.loader = loader;
    }

    @Override
    public Response<PluginInfo> apply(PluginResource resource, Input input)
        throws BadRequestException, MethodNotAllowedException, IOException {
      return new InstallPlugin(loader, resource.getName(), false)
          .apply(TopLevelResource.INSTANCE, input);
    }
  }
}
