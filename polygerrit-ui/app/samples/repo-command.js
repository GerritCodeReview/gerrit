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

class RepoCommandLow extends Polymer.Element {
  static get is() { return 'repo-command-low'; }

  static get properties() {
    return {
      rootUrl: String,
    };
  }

  static get template() {
    return Polymer.html`
      <style include="shared-styles">
      :host {
        display: block;
        margin-bottom: var(--spacing-xxl);
      }
      </style>
      <h3>Plugin Bork</h3>
      <gr-button on-click="_handleCommandTap">Bork</gr-button>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
    console.info(this.repoName);
    console.info(this.config);
    this.hidden = this.repoName !== 'All-Projects';
  }

  _handleCommandTap() {
    alert('bork');
  }
}

customElements.define(RepoCommandLow.is, RepoCommandLow);

/**
 * This plugin adds a new command to the command page of the repo All-Projects.
 */
Gerrit.install(plugin => {
  plugin.registerCustomComponent(
      'repo-command', 'repo-command-low');
});
