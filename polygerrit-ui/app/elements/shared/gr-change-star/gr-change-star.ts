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
import {ChangeInfo} from '../../../types/common';
import {fireAlert} from '../../../utils/event-util';
import {
  Shortcut,
  ShortcutSection,
} from '../../../services/shortcuts/shortcuts-config';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

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
export class GrChangeStar extends LitElement {
  /**
   * Fired when star state is toggled.
   *
   * @event toggle-star
   */

  @property({type: Object})
  change?: ChangeInfo;

  private readonly shortcuts = getAppContext().shortcutsService;

  static override get styles() {
    return [
      sharedStyles,
      css`
        button {
          background-color: transparent;
          cursor: pointer;
        }
        iron-icon.active {
          fill: var(--link-color);
        }
        iron-icon {
          vertical-align: top;
          --iron-icon-height: var(
            --gr-change-star-size,
            var(--line-height-normal, 20px)
          );
          --iron-icon-width: var(
            --gr-change-star-size,
            var(--line-height-normal, 20px)
          );
        }
        :host([hidden]) {
          visibility: hidden;
          display: block !important;
        }
      `,
    ];
  }

  override render() {
    return html`
      <button
        role="checkbox"
        title=${this.shortcuts.createTitle(
          Shortcut.TOGGLE_CHANGE_STAR,
          ShortcutSection.ACTIONS
        )}
        aria-label=${this.change?.starred
          ? 'Unstar this change'
          : 'Star this change'}
        @click=${this.toggleStar}
      >
        <iron-icon
          class=${this.change?.starred ? 'active' : ''}
          .icon=${`gr-icons:star${this.change?.starred ? '' : '-border'}`}
        ></iron-icon>
      </button>
    `;
  }

  toggleStar() {
    // Note: change should always be defined when use gr-change-star
    // but since we don't have a good way to enforce usage to always
    // set the change, we still check it here.
    if (!this.change) return;

    const newVal = !this.change.starred;
    this.change.starred = newVal;
    this.requestUpdate('change');
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
}
