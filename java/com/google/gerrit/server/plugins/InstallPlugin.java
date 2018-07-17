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
import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCreateView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.zip.ZipException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public class InstallPlugin implements RestModifyView<TopLevelResource, InstallPluginInput> {
  private final PluginLoader loader;

  private String name;
  private boolean created;

  @Inject
  InstallPlugin(PluginLoader loader) {
    this.loader = loader;
  }

  public InstallPlugin setName(String name) {
    this.name = name;
    return this;
  }

  public InstallPlugin setCreated(boolean created) {
    this.created = created;
    return this;
  }

  @Override
  public Response<PluginInfo> apply(TopLevelResource resource, InstallPluginInput input)
      throws RestApiException, IOException {
    loader.checkRemoteAdminEnabled();
    try {
      try (InputStream in = openStream(input)) {
        String pluginName = loader.installPluginFromStream(name, in);
        PluginInfo info = ListPlugins.toPluginInfo(loader.get(pluginName));
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

  private InputStream openStream(InstallPluginInput input) throws IOException, BadRequestException {
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
  static class Create
      implements RestCreateView<TopLevelResource, PluginResource, InstallPluginInput> {
    private final PluginLoader loader;
    private final Provider<InstallPlugin> install;

    @Inject
    Create(PluginLoader loader, Provider<InstallPlugin> install) {
      this.loader = loader;
      this.install = install;
    }

    @Override
    public Response<PluginInfo> apply(
        TopLevelResource parentResource, IdString id, InstallPluginInput input) throws Exception {
      loader.checkRemoteAdminEnabled();
      return install.get().setName(id.get()).setCreated(true).apply(parentResource, input);
    }
  }

  @RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
  static class Overwrite implements RestModifyView<PluginResource, InstallPluginInput> {
    private final Provider<InstallPlugin> install;

    @Inject
    Overwrite(Provider<InstallPlugin> install) {
      this.install = install;
    }

    @Override
    public Response<PluginInfo> apply(PluginResource resource, InstallPluginInput input)
        throws RestApiException, IOException {
      return install.get().setName(resource.getName()).apply(TopLevelResource.INSTANCE, input);
    }
  }
}
