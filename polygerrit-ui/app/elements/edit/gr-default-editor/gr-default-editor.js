/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  class GrDefaultEditor extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-default-editor'; }
    /**
     * Fired when the content of the editor changes.
     *
     * @event content-change
     */

    static get properties() {
      return {
        fileContent: String,
      };
    }

    _handleTextareaInput(e) {
      this.dispatchEvent(new CustomEvent(
          'content-change',
          {detail: {value: e.target.value}, bubbles: true, composed: true}));
    }
  }

  customElements.define(GrDefaultEditor.is, GrDefaultEditor);
})();
