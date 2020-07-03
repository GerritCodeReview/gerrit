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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.plugins.ListPlugins;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.util.cli.Options;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Map;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.VIEW_PLUGINS)
@CommandMetaData(name = "ls", description = "List the installed plugins", runsAt = MASTER_OR_SLAVE)
public class PluginLsCommand extends SshCommand {
  @Inject @Options public ListPlugins list;

  @Option(name = "--format", usage = "output format")
  private OutputFormat format = OutputFormat.TEXT;

  @Override
  public void run() throws Exception {
    Map<String, PluginInfo> output = list.apply(TopLevelResource.INSTANCE);

    if (format.isJson()) {
      format
          .newGson()
          .toJson(output, new TypeToken<Map<String, PluginInfo>>() {}.getType(), stdout);
      stdout.print('\n');
    } else {
      String template = "%-30s %-10s %-16s %-8s %s\n";
      stdout.format(template, "Name", "Version", "Api-Version", "Status", "File");
      stdout.print(
          "-------------------------------------------------------------------------------\n");
      for (Map.Entry<String, PluginInfo> p : output.entrySet()) {
        PluginInfo info = p.getValue();
        stdout.format(
            template,
            p.getKey(),
            Strings.nullToEmpty(info.version),
            Strings.nullToEmpty(info.apiVersion),
            status(info.disabled),
            Strings.nullToEmpty(info.filename));
      }
    }
    stdout.flush();
  }

  private String status(Boolean disabled) {
    return disabled != null && disabled.booleanValue() ? "DISABLED" : "ENABLED";
  }
}
