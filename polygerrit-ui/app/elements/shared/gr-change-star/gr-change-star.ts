/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo} from '../../../types/common';
import {fireAlert} from '../../../utils/event-util';
import {
  Shortcut,
  ShortcutSection,
} from '../../../services/shortcuts/shortcuts-config';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {resolve} from '../../../models/dependency';
import {shortcutsServiceToken} from '../../../services/shortcuts/shortcuts-service';

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

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  static override get styles() {
    return [
      sharedStyles,
      css`
        button {
          background-color: transparent;
          cursor: pointer;
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
        title=${this.getShortcutsService().createTitle(
          Shortcut.TOGGLE_CHANGE_STAR,
          ShortcutSection.ACTIONS
        )}
        aria-label=${this.change?.starred
          ? 'Unstar this change'
          : 'Star this change'}
        @click=${this.toggleStar}
      >
        <gr-icon
          icon="grade"
          ?filled=${!!this.change?.starred}
          class=${this.change?.starred ? 'active' : ''}
        ></gr-icon>
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
