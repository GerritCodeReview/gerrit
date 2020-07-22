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

// Element class exists in all browsers:
// https://developer.mozilla.org/en-US/docs/Web/API/Element
// Rename it to PolymerElement to avoid conflicts. Also,
// typescript reports the following error:
// error TS2451: Cannot redeclare block-scoped variable 'Element'.
const {html, Element: PolymerElement} = Polymer;

class RepoCommandLow extends PolymerElement {
  static get is() { return 'repo-command-low'; }

  static get properties() {
    return {
      rootUrl: String,
    };
  }

  static get template() {
    return html`
    <style include="shared-styles">
    :host {
      display: block;
      margin-bottom: var(--spacing-xxl);
    }
    </style>
    <h3>Low-level bork</h3>
    <gr-button
      on-click="_handleCommandTap"
    >
      Low-level bork
    </gr-button>
   `;
  }

  connectedCallback() {
    super.connectedCallback();
    console.info(this.repoName);
    console.info(this.config);
    this.hidden = this.repoName !== 'All-Projects';
  }

  _handleCommandTap() {
    alert('(softly) bork, bork.');
  }
}

// register the custom component
customElements.define(RepoCommandLow.is, RepoCommandLow);

/**
 * This plugin will add two new commands in command page for
 * All-Projects.
 *
 * The added commands will simply alert you when click.
 */
Gerrit.install(plugin => {
  // High-level API
  plugin.project()
      .createCommand('Bork', (repoName, projectConfig) => {
        if (repoName !== 'All-Projects') {
          return false;
        }
      })
      .onTap(() => {
        alert('Bork, bork!');
      });

  // Low-level API
  plugin.registerCustomComponent(
      'repo-command', 'repo-command-low');
});
