// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Set;

/**
 * Extension point to provide soy templates that should be registered so that they can be used for
 * sending emails from a plugin.
 */
@ExtensionPoint
public interface MailSoyTemplateProvider {
  /**
   * Return the name of the resource path that contains the soy template files that are returned by
   * {@link #getFileNames()}.
   *
   * @return resource path of the templates
   */
  String getPath();

  /**
   * Return the names of the soy template files.
   *
   * <p>These files are expected to exist in the resource path that is returned by {@link
   * #getPath()}.
   *
   * @return names of the template files, including the {@code .soy} file extension
   */
  Set<String> getFileNames();
}
