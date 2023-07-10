// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.gerrit.extensions.webui.UiAction;
import java.util.List;
import java.util.Objects;

/**
 * Representation of an action in the REST API.
 *
 * <p>This class determines the JSON format of actions in the REST API.
 *
 * <p>An action describes a REST API call the client can make to manipulate a resource. These are
 * frequently implemented by plugins and may be discovered at runtime.
 */
public class ActionInfo {
  /**
   * HTTP method to use with the action. Most actions use {@code POST}, {@code PUT} or {@code
   * DELETE} to cause state changes.
   */
  public String method;

  /**
   * Short title to display to a user describing the action. In the Gerrit web interface the label
   * is used as the text on the button that is presented in the UI.
   */
  public String label;

  /**
   * Longer text to display describing the action. In a web UI this should be the title attribute of
   * the element, displaying when the user hovers the mouse.
   */
  public String title;

  /**
   * If {@code true} the action is permitted at this time and the caller is likely allowed to
   * execute it. This may change if state is updated at the server or permissions are modified.
   */
  public Boolean enabled;

  /**
   * Optional list of enabled options.
   *
   * <p>For the {@code rebase} REST view the following options are supported:
   *
   * <ul>
   *   <li>{@code rebase}: Present if the user can rebase the change. This is the case for the
   *       change owner and users with the {@code Submit} or {@code Rebase} permission if they have
   *       the {@code Push} permission.
   *   <li>{@code rebase_on_behalf_of}: Present if the user can rebase the change on behalf of the
   *       uploader. This is the case for the change owner and users with the {@code Submit} or
   *       {@code Rebase} permission.
   * </ul>
   *
   * <p>For all other REST views no options are returned.
   */
  public List<String> enabledOptions;

  public ActionInfo(UiAction.Description d) {
    method = d.getMethod();
    label = d.getLabel();
    title = d.getTitle();
    enabled = d.isEnabled() ? true : null;
    enabledOptions = d.getEnabledOptions();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ActionInfo) {
      ActionInfo actionInfo = (ActionInfo) o;
      return Objects.equals(method, actionInfo.method)
          && Objects.equals(label, actionInfo.label)
          && Objects.equals(title, actionInfo.title)
          && Objects.equals(enabled, actionInfo.enabled)
          && Objects.equals(enabledOptions, actionInfo.enabledOptions);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, label, title, enabled, enabledOptions);
  }

  public ActionInfo() {}
}
