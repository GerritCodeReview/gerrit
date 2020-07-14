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
 * This plugin will add a text next to WIP requirement if shown.
 */
class WipRequirementValue extends Polymer.Element {
  static get is() {
    return 'wip-requirement-value';
  }

  static get template() {
    return Polymer.html`
        <style include="shared-styles">
        :host {
          color: var(--deemphasized-text-color);
        }
        </style>
        <span>Will be removed once active.</span>
      `;
  }

  static get properties() {
    return {
      change: Object,
      requirement: Object,
    };
  }
}

customElements.define(WipRequirementValue.is, WipRequirementValue);

Gerrit.install(plugin => {
  plugin.registerCustomComponent(
      'submit-requirement-item-wip', WipRequirementValue.is, {slot: 'value'});
});