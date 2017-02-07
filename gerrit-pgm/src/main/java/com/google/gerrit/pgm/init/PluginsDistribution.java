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

package com.google.gerrit.pgm.init;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Represents the plugins packaged in the Gerrit distribution */
public interface PluginsDistribution {

  public interface Processor {
    /**
     * @param pluginName the name of the plugin (without the .jar extension)
     * @param in the content of the plugin .jar file. Implementors don't have to close this stream.
     * @throws IOException implementations will typically propagate any IOException caused by
     *     dealing with the InputStream back to the caller
     */
    void process(String pluginName, InputStream in) throws IOException;
  }

  /**
   * Iterate over plugins package in the Gerrit distribution
   *
   * @param processor invoke for each plugin via its process method
   * @throws FileNotFoundException if the location of the plugins couldn't be determined
   * @throws IOException in case of any other IO error caused by reading the plugin input stream
   */
  void foreach(Processor processor) throws FileNotFoundException, IOException;

  /**
   * List plugins included in the Gerrit distribution
   *
   * @return list of plugins names included in the Gerrit distribution
   * @throws FileNotFoundException if the location of the plugins couldn't be determined
   */
  List<String> listPluginNames() throws FileNotFoundException;
}
