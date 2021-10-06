/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../gr-icons/gr-icons';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-star_html';
import {customElement, property} from '@polymer/decorators';
import {ChangeInfo} from '../../../types/common';
import {fireAlert} from '../../../utils/event-util';
import {
  Shortcut,
  ShortcutSection,
} from '../../../services/shortcuts/shortcuts-config';
import {appContext} from '../../../services/app-context';

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-star': GrChangeStar;
  }
}

export interface ChangeStarToggleStarDetail {
  change: ChangeInfo;
  starred: boolean;
}

@customElement('gr-change-star')
export class GrChangeStar extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when star state is toggled.
   *
   * @event toggle-star
   */

  @property({type: Object, notify: true})
  change?: ChangeInfo;

  private readonly shortcuts = appContext.shortcutsService;

  _computeStarClass(starred?: boolean) {
    return starred ? 'active' : '';
  }

  _computeStarIcon(starred?: boolean) {
    // Hollow star is used to indicate inactive state.
    return `gr-icons:star${starred ? '' : '-border'}`;
  }

  _computeAriaLabel(starred?: boolean) {
    return starred ? 'Unstar this change' : 'Star this change';
  }

  toggleStar() {
    // Note: change should always be defined when use gr-change-star
    // but since we don't have a good way to enforce usage to always
    // set the change, we still check it here.
    if (!this.change) {
      return;
    }
    const newVal = !this.change.starred;
    this.set('change.starred', newVal);
    const detail: ChangeStarToggleStarDetail = {
      change: this.change,
      starred: newVal,
    };
    if (newVal) fireAlert(this, 'Starring change...');
    this.dispatchEvent(
      new CustomEvent('toggle-star', {
        bubbles: true,
        composed: true,
        detail,
      })
    );
  }

  createTitle(shortcutName: Shortcut, section: ShortcutSection) {
    return this.shortcuts.createTitle(shortcutName, section);
  }
}
