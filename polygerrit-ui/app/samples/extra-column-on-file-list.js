/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This plugin will an extra column to file list on change page to show
 * the first character of the path.
 */

// Header of this extra column
class ColumnHeader extends Polymer.Element {
  static get is() { return 'column-header'; }

  static get template() {
    return Polymer.html`
      <style>
      :host {
        display: block;
        padding-right: var(--spacing-m);
        min-width: 5em;
      }
      </style>
      <div>First Char</div>
    `;
  }
}

customElements.define(ColumnHeader.is, ColumnHeader);

// Content of this extra column
class ColumnContent extends Polymer.Element {
  static get is() { return 'column-content'; }

  static get properties() {
    return {
      path: String,
    };
  }

  static get template() {
    return Polymer.html`
      <style>
      :host {
        display:block;
        padding-right: var(--spacing-m);
        min-width: 5em;
      }
      </style>
      <div>[[getStatus(path)]]</div>
    `;
  }

  getStatus(path) {
    return path.charAt(0);
  }
}

customElements.define(ColumnContent.is, ColumnContent);

Gerrit.install(plugin => {
  plugin.registerDynamicCustomComponent(
      'change-view-file-list-header-prepend', ColumnHeader.is);
  plugin.registerDynamicCustomComponent(
      'change-view-file-list-content-prepend', ColumnContent.is);
});
