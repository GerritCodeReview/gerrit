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

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Locator of Gerrit distribution
 */
public interface GerritDistributionLocator {

  /**
   * Locates Gerrit distribution at runtime
   *
   * This could be the location of the gerrit.war itself or of the folder
   * containing unzipped gerrit.war
   *
   * @return the location of Gerrit distribution
   * @throws FileNotFoundException if the location couldn't be determined
   */
  public File locate() throws FileNotFoundException;
}
