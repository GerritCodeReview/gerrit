// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.gerrit.pgm.init.InitPlugins.JAR;
import static com.google.gerrit.pgm.init.InitPlugins.PLUGIN_DIR;

import com.google.gerrit.pgm.init.PluginsDistribution;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;

@Singleton
class UnzippedDistribution implements PluginsDistribution {

  private ServletContext servletContext;
  private File pluginsDir;

  UnzippedDistribution(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  @Override
  public void foreach(Processor processor) throws FileNotFoundException, IOException {
    File[] list = getPluginsDir().listFiles();
    if (list != null) {
      for (File p : list) {
        String pluginJarName = p.getName();
        String pluginName = pluginJarName.substring(0, pluginJarName.length() - JAR.length());
        try (InputStream in = new FileInputStream(p)) {
          processor.process(pluginName, in);
        }
      }
    }
  }

  @Override
  public List<String> listPluginNames() throws FileNotFoundException {
    List<String> names = new ArrayList<>();
    String[] list = getPluginsDir().list();
    if (list != null) {
      for (String pluginJarName : list) {
        String pluginName = pluginJarName.substring(0, pluginJarName.length() - JAR.length());
        names.add(pluginName);
      }
    }
    return names;
  }

  private File getPluginsDir() {
    if (pluginsDir == null) {
      File root = new File(servletContext.getRealPath(""));
      pluginsDir = new File(root, PLUGIN_DIR);
    }
    return pluginsDir;
  }
}
