// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.config;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;

import java.util.HashSet;
import java.util.Set;

public class DownloadInfo extends JavaScriptObject {
  public final Set<String> schemes() {
    return Natives.keys(_schemes());
  }
  public final native DownloadSchemeInfo scheme(String n) /*-{ return this.schemes[n]; }-*/;
  private final native NativeMap<DownloadSchemeInfo> _schemes() /*-{ return this.schemes; }-*/;

  protected DownloadInfo() {
  }

  public static class DownloadSchemeInfo extends JavaScriptObject {
    public final Set<String> commandNames() {
      return Natives.keys(_commands());
    }

    public final Set<DownloadCommandInfo> commands(String project) {
      Set<DownloadCommandInfo> commands = new HashSet<>();
      for (String commandName : commandNames()) {
        commands.add(new DownloadCommandInfo(commandName, command(commandName,
            project)));
      }
      return commands;
    }

    public final String command(String commandName, String project) {
      return command(commandName).replaceAll("\\$\\{project\\}", project);
    }

    public final String getUrl(String project) {
      return url().replaceAll("\\$\\{project\\}", project);
    }

    public final native String name() /*-{ return this.name; }-*/;
    public final native String url() /*-{ return this.url; }-*/;
    public final native boolean isAuthRequired() /*-{ return this.is_auth_required || false; }-*/;
    public final native boolean isAuthSupported() /*-{ return this.is_auth_supported || false; }-*/;
    public final native String command(String n) /*-{ return this.commands[n]; }-*/;
    private final native NativeMap<NativeString> _commands() /*-{ return this.commands; }-*/;

    protected DownloadSchemeInfo() {
    }
  }

  public static class DownloadCommandInfo {
    private final String name;
    private final String command;

    DownloadCommandInfo(String name, String command) {
      this.name = name;
      this.command = command;
    }

    public String name() {
      return name;
    }

    public String command() {
      return command;
    }
  }
}
