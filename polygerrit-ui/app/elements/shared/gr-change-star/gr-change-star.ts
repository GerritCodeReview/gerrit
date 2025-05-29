/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-icon/gr-icon';
import {ChangeInfo} from '../../../types/common';
import {
  Shortcut,
  ShortcutSection,
} from '../../../services/shortcuts/shortcuts-config';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {shortcutsServiceToken} from '../../../services/shortcuts/shortcuts-service';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';

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
        @click=${this.handleClick}
      >
        <gr-icon
          icon="star"
          small
          ?filled=${!!this.change?.starred}
          class=${this.change?.starred ? 'active' : ''}
        ></gr-icon>
      </button>
    `;
  }

  handleClick(e: Event) {
    e.stopPropagation();
    this.toggleStar();
  }

  toggleStar() {
    assertIsDefined(this.change, 'change');

    const newVal = !this.change.starred;
    this.change.starred = newVal;
    this.requestUpdate('change');
    const detail: ChangeStarToggleStarDetail = {
      change: this.change,
      starred: newVal,
    };
    fire(this, 'toggle-star', detail);
  }
}
