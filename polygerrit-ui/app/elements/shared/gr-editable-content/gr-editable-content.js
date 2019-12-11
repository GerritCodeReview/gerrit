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

  const RESTORED_MESSAGE = 'Content restored from a previous edit.';
  const STORAGE_DEBOUNCE_INTERVAL_MS = 400;

  /**
   * @appliesMixin Gerrit.FireMixin
   */
  class GrEditableContent extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-editable-content'; }
    /**
     * Fired when the save button is pressed.
     *
     * @event editable-content-save
     */

    /**
     * Fired when the cancel button is pressed.
     *
     * @event editable-content-cancel
     */

    /**
     * Fired when content is restored from storage.
     *
     * @event show-alert
     */

    static get properties() {
      return {
        content: {
          notify: true,
          type: String,
        },
        disabled: {
          reflectToAttribute: true,
          type: Boolean,
          value: false,
        },
        editing: {
          observer: '_editingChanged',
          type: Boolean,
          value: false,
        },
        removeZeroWidthSpace: Boolean,
        // If no storage key is provided, content is not stored.
        storageKey: String,
        _saveDisabled: {
          computed: '_computeSaveDisabled(disabled, content, _newContent)',
          type: Boolean,
          value: true,
        },
        _newContent: {
          type: String,
          observer: '_newContentChanged',
        },
      };
    }

    focusTextarea() {
      this.$$('iron-autogrow-textarea').textarea.focus();
    }

    _newContentChanged(newContent, oldContent) {
      if (!this.storageKey) { return; }

      this.debounce('store', () => {
        if (newContent.length) {
          this.$.storage.setEditableContentItem(this.storageKey, newContent);
        } else {
          this.$.storage.eraseEditableContentItem(this.storageKey);
        }
      }, STORAGE_DEBOUNCE_INTERVAL_MS);
    }

    _editingChanged(editing) {
      if (!editing) { return; }

      let content;
      if (this.storageKey) {
        const storedContent =
            this.$.storage.getEditableContentItem(this.storageKey);
        if (storedContent && storedContent.message) {
          content = storedContent.message;
          this.dispatchEvent(new CustomEvent('show-alert', {
            detail: {message: RESTORED_MESSAGE},
            bubbles: true,
            composed: true,
          }));
        }
      }
      if (!content) {
        content = this.content || '';
      }

      // TODO(wyatta) switch linkify sequence, see issue 5526.
      this._newContent = this.removeZeroWidthSpace ?
        content.replace(/^R=\u200B/gm, 'R=') :
        content;
    }

    _computeSaveDisabled(disabled, content, newContent) {
      // Polymer 2: check for undefined
      if ([
        disabled,
        content,
        newContent,
      ].some(arg => arg === undefined)) {
        return true;
      }

      return disabled || (content === newContent);
    }

    _handleSave(e) {
      e.preventDefault();
      this.fire('editable-content-save', {content: this._newContent});
    }

    _handleCancel(e) {
      e.preventDefault();
      this.editing = false;
      this.fire('editable-content-cancel');
    }
  }

  customElements.define(GrEditableContent.is, GrEditableContent);
})();
