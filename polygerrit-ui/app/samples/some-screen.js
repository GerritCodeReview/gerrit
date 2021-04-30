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

class SomeScreenMain extends Polymer.Element {
  static get is() { return 'some-screen-main'; }

  static get properties() {
    return {
      rootUrl: String,
    };
  }

  static get template() {
    return Polymer.html`
      This is the <b>main</b> plugin screen at [[token]]
      <ul>
        <li><a href$="[[rootUrl]]/bar">without component</a></li>
      </ul>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
    this.rootUrl = `${this.plugin.screenUrl()}`;
  }
}

customElements.define(SomeScreenMain.is, SomeScreenMain);

/**
 * This plugin will add several things to gerrit:
 * 1. two screens added by this plugin in two different ways
 * 2. a link in change page under meta info to the added main screen
 */
Gerrit.install(plugin => {
  // Recommended approach for screen() API.
  plugin.screen('main', 'some-screen-main');

  const mainUrl = plugin.screenUrl('main');

  // Quick and dirty way to get something on screen.
  plugin.screen('bar').onAttached(el => {
    el.innerHTML = `This is a plugin screen at ${el.token}<br/>` +
    `<a href="${mainUrl}">Go to main plugin screen</a>`;
  });

  // Add a "Plugin screen" link to the change view screen.
  plugin.hook('change-metadata-item').onAttached(el => {
    el.innerHTML = `<a href="${mainUrl}">Plugin screen</a>`;
  });
});
