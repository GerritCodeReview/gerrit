/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  const Defs = {};

  /**
   * @typedef {{
   *   provider: string,
   *   keySpecs: KeySpec[],
   * }}
   */
  Defs.ProvisionedKeySetSpec;

  /**
   * @typedef {{
   *   binding: (string|string[][]),
   *   description: string,
   * }}
   */
  Defs.KeySpec;

  Polymer({
    is: 'gr-keyboard-shortcuts-directory',

    properties: {
      _directory: {
        type: Object,
        value: new Map(), // Intentional to share the object across instances.
      },

      _instances: {
        type: Object,
        value: new Set(), // Intentional to share the object across instances.
      },
    },

    attached() {
      this._instances.add(this);
      console.log('attaching instance #' + this._instances.size);
    },

    detached() {
      this._instances.delete(this);
    },

    attachProvider(provider) {
      this._directory.set(provider.localName, provider.keyBindingDocs);
      this._notifyAllInstances();
    },

    detachProvider(provider) {
      this._directory.delete(provider.localName);
      this._notifyAllInstances();
    },

    _notifyAllInstances() {
      this._instances.forEach(instance => instance._fireUpdatedEvent());
    },

    _normalizeKeySpec(keySpec) {
      const binding = keySpec.binding instanceof Array ?
          keySpec.binding :
          [[keySpec.binding]];
      const result = {binding, description: keySpec.description};
      return result;
    },

    _buildNormalizedView() {
      const view = new Map();
      this._directory.forEach((sections, providerName) => {
        for (const section of Object.keys(sections)) {
          if (!view.has(section)) {
            view.set(section, []);
          }
          sections[section].forEach(ks => {
            view.get(section).push(this._normalizeKeySpec(ks));
          });
        }
      });
      return view;
    },

    _fireUpdatedEvent() {
      const detail = this._buildNormalizedView();
      this.dispatchEvent(new CustomEvent('keyboard-shortcuts-updated',
          {detail}));
    },
  })
})();
