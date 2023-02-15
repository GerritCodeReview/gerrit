/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {magicModelToken} from '../../../embed/diff/gr-diff-model/magic-model';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';

@customElement('gr-file-list-magic')
export class GrFileListMagic extends LitElement {
  private readonly getMagic = resolve(this, magicModelToken);

  private readonly getUser = resolve(this, userModelToken);

  @query('#searchInput')
  searchInput?: HTMLInputElement;

  @state() search = '';

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          border-top: 1px solid var(--border-color);
          display: block;
          padding: 4px 8px;
        }
        gr-icon.green {
          color: var(--green-400);
        }
        input {
          padding: var(--spacing-xs) var(--spacing-s);
        }
        .flex {
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getMagic().search$,
      x => (this.search = x)
    );
  }

  protected override render() {
    return html`
            <div class="flex">
    <div class="left">
      <gr-icon class="green" filled icon="science"></gr-icon>
      <span>Magic</span>
    </div>  
    <div class="middle">
      Search: <input id="searchInput" type="text" .value=${this.search} @input=${this.onSearchInputChange}></input>
    </div>  
    <div class="right">
      <gr-button flatten @click=${this.onHideBothClick}><gr-icon icon="hide"></gr-icon></gr-button>
      <gr-button flatten @click=${this.onHideControlsClick}><gr-icon icon="hide"></gr-icon></gr-button>
      <gr-button flatten @click=${this.onHideFileNameRowClick}><gr-icon icon="hide"></gr-icon></gr-button>
      <gr-button flatten @click=${this.onHideHeaderRowClick}><gr-icon icon="hide"></gr-icon></gr-button>
    </div>  
    </div>  
     `;
  }

  onHideBothClick() {
    const current = this.getMagic().getState().hideBoth;
    this.getMagic().updateState({hideBoth: !current});
  }

  onHideControlsClick() {
    const current = this.getMagic().getState().hideControls;
    this.getMagic().updateState({hideControls: !current});
  }

  onHideFileNameRowClick() {
    const current = this.getMagic().getState().hideFileNameRow;
    this.getMagic().updateState({hideFileNameRow: !current});
  }

  onHideHeaderRowClick() {
    const current = this.getMagic().getState().hideHeaderRow;
    this.getMagic().updateState({hideHeaderRow: !current});
  }

  onHideContextClick() {
    const currentState = this.getUser().getState();
    const currentPrefs =
      this.getUser().getState().diffPreferences ?? createDefaultDiffPrefs();
    const context = currentPrefs.context === 0 ? 10 : 0;
    this.getUser().updateState({
      ...currentState,
      diffPreferences: {...currentPrefs, context},
    });
  }

  onSearchInputChange() {
    if (!this.searchInput) return;
    this.getMagic().updateState({search: this.searchInput.value});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-magic': GrFileListMagic;
  }
}
