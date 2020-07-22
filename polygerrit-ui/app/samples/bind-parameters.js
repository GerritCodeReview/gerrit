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

class MyBindSample extends PolymerElement {
  static get is() { return 'my-bind-sample'; }

  static get properties() {
    return {
      computedExample: {
        type: String,
        computed: '_computeExample(revision._number)',
      },
      revision: {
        type: Object,
        observer: '_onRevisionChanged',
      },
    };
  }

  static get template() {
    return html`
    Template example: Patchset number [[revision._number]]. <br/>
    Computed example: [[computedExample]].
    `;
  }

  _computeExample(value) {
    if (!value) { return '(empty)'; }
    return `(patchset ${value} selected)`;
  }

  _onRevisionChanged(value) {
    console.info(`(attributeHelper.bind) revision number: ${value._number}`);
  }
}

// register the custom component
customElements.define(MyBindSample.is, MyBindSample);

/**
 * This plugin will add a new section
 * between the file list and change log with the
 * `my-bind-sample` component.
 */
Gerrit.install(plugin => {
  // You should see the above text with the right revision number shown
  // between the file list and the change log
  plugin.registerCustomComponent(
      'change-view-integration', 'my-bind-sample');
});
