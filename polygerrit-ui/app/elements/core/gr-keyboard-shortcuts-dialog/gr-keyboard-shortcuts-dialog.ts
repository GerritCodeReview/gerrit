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
import '../../shared/gr-button/gr-button';
import '../gr-key-binding-display/gr-key-binding-display';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-keyboard-shortcuts-dialog_html';
import {
  KeyboardShortcutMixin,
  ShortcutSection,
  ShortcutListener,
  SectionView,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {property, customElement} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-keyboard-shortcuts-dialog': GrKeyboardShortcutsDialog;
  }
}

@customElement('gr-keyboard-shortcuts-dialog')
export class GrKeyboardShortcutsDialog extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @property({type: Array})
  _left?: unknown[];

  @property({type: Array})
  _right?: unknown[];

  private keyboardShortcutDirectoryListener: ShortcutListener;

  constructor() {
    super();
    this.keyboardShortcutDirectoryListener = this._onDirectoryUpdated.bind(
      this
    );
    super.addKeyboardShortcutDirectoryListener(
      this.keyboardShortcutDirectoryListener
    );
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'dialog');
  }

  /** @override */
  attached() {
    super.attached();
  }

  /** @override */
  detached() {
    super.detached();
    super.removeKeyboardShortcutDirectoryListener(
      this.keyboardShortcutDirectoryListener
    );
  }

  _handleCloseTap(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('close', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _onDirectoryUpdated(directory?: Map<ShortcutSection, SectionView>) {
    if (!directory) {
      return;
    }
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
