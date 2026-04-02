/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-icon/gr-icon';
import {
  ChangeInfo,
  ListChangesOption,
  NumericChangeId,
} from '../../../types/common';
import {
  Shortcut,
  ShortcutSection,
} from '../../../services/shortcuts/shortcuts-config';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {shortcutsServiceToken} from '../../../services/shortcuts/shortcuts-service';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';
import {restApiServiceToken} from '../../../services/gr-rest-api/gr-rest-api';
import {listChangesOptionsToHex} from '../../../utils/change-util';

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

  @property({type: Number})
  changeNum?: NumericChangeId;

  @state()
  change?: ChangeInfo;

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  private readonly restApiService = resolve(this, restApiServiceToken);

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

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('changeNum') && this.changeNum !== undefined) {
      this.fetchChange();
    }
  }

  private async fetchChange() {
    if (this.changeNum === undefined) return;
    const change = await this.restApiService.getChange(
      this.changeNum,
      undefined,
      listChangesOptionsToHex(ListChangesOption.STAR)
    );
    this.change = change;
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
    this.change = {
      ...this.change,
      starred: newVal,
    };
    const detail: ChangeStarToggleStarDetail = {
      change: this.change,
      starred: newVal,
    };
    fire(this, 'toggle-star', detail);
  }
}
