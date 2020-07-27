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
import '../../shared/gr-button/gr-button.js';
import '../gr-key-binding-display/gr-key-binding-display.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-keyboard-shortcuts-dialog_html.js';
import {KeyboardShortcutMixin, ShortcutSection} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';

/**
 * @extends PolymerElement
 */
class GrKeyboardShortcutsDialog extends KeyboardShortcutMixin(
    GestureEventListeners(
        LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-keyboard-shortcuts-dialog'; }
  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  static get properties() {
    return {
      _left: Array,
      _right: Array,
    };
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  /** @override */
  attached() {
    super.attached();
    this.keyboardShortcutDirectoryListener =
        this._onDirectoryUpdated.bind(this);
    this.addKeyboardShortcutDirectoryListener(
        this.keyboardShortcutDirectoryListener);
  }

  /** @override */
  detached() {
    super.detached();
    this.removeKeyboardShortcutDirectoryListener(
        this.keyboardShortcutDirectoryListener);
  }

  _handleCloseTap(e) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('close', {
      composed: true, bubbles: true,
    }));
  }

  _onDirectoryUpdated(directory) {
    const left = [];
    const right = [];

    if (directory.has(ShortcutSection.EVERYWHERE)) {
      left.push({
        section: ShortcutSection.EVERYWHERE,
        shortcuts: directory.get(ShortcutSection.EVERYWHERE),
      });
    }

    if (directory.has(ShortcutSection.NAVIGATION)) {
      left.push({
        section: ShortcutSection.NAVIGATION,
        shortcuts: directory.get(ShortcutSection.NAVIGATION),
      });
    }

    if (directory.has(ShortcutSection.ACTIONS)) {
      right.push({
        section: ShortcutSection.ACTIONS,
        shortcuts: directory.get(ShortcutSection.ACTIONS),
      });
    }

    if (directory.has(ShortcutSection.REPLY_DIALOG)) {
      right.push({
        section: ShortcutSection.REPLY_DIALOG,
        shortcuts: directory.get(ShortcutSection.REPLY_DIALOG),
      });
    }

    if (directory.has(ShortcutSection.FILE_LIST)) {
      right.push({
        section: ShortcutSection.FILE_LIST,
        shortcuts: directory.get(ShortcutSection.FILE_LIST),
      });
    }

    if (directory.has(ShortcutSection.DIFFS)) {
      right.push({
        section: ShortcutSection.DIFFS,
        shortcuts: directory.get(ShortcutSection.DIFFS),
      });
    }

    this.set('_left', left);
    this.set('_right', right);
  }
}

customElements.define(GrKeyboardShortcutsDialog.is,
    GrKeyboardShortcutsDialog);
