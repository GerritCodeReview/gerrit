/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  magicModelToken,
  Replacement,
} from '../../../embed/diff/gr-diff-model/magic-model';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';

@customElement('gr-file-list-magic')
export class GrFileListMagic extends LitElement {
  private readonly getMagic = resolve(this, magicModelToken);

  private readonly getUser = resolve(this, userModelToken);

  @query('#searchInput')
  searchInput?: HTMLInputElement;

  @query('#ignoreInput')
  ignoreInput?: HTMLInputElement;

  @state() search = '';

  @state() ignore = '';

  @state() replacementCounts: Replacement[] = [];

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
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
        div.replacements-container {
          display: flex;
          justify-content: center;
          align-items: center;
        }
        table.replacements {
          border-top: 1px solid var(--border-color);
          border-bottom: 1px solid var(--border-color);
        }
        table.replacements th {
          border-left: 1px solid var(--border-color);
          border-right: 1px solid var(--border-color);
          border-bottom: 1px solid var(--border-color);
          padding: 0 var(--spacing-m);
        }
        table.replacements td {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          padding: 0 var(--spacing-m);
          border-left: 1px solid var(--border-color);
          border-right: 1px solid var(--border-color);
        }
        table.replacements td.count,
        table.replacements td.ignore {
          text-align: center;
        }
        table.replacements td.left {
          background-color: var(--light-remove-highlight-color);
        }
        table.replacements td.right {
          background-color: var(--light-add-highlight-color);
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
    subscribe(
      this,
      () => this.getMagic().replacementCounts$,
      x => (this.replacementCounts = x.slice(0, 10))
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
      Search: <input id="searchInput" type="text" .value=${
        this.search
      } @input=${this.onSearchInputChange}></input>
      Ignore: <input id="ignoreInput" type="text" .value=${
        this.ignore
      } @input=${this.onIgnoreInputChange}></input>
    </div>  
    <div class="right">
      <gr-button flatten @click=${
        this.onFadedDiffClick
      }><gr-icon icon="format_paint"></gr-icon></gr-button>
      <gr-button flatten @click=${
        this.onHideClick
      }><gr-icon icon="hide"></gr-icon></gr-button>
    </div>  
    </div>
    ${when(
      this.replacementCounts.length > 0,
      () => html`
    <div class="replacements-container">
    <table class="replacements">
      <tr>
        <th colspan="4" class="heading-3">TOP 10 Code Replacements</th>
      </tr>
      <tr>
        <th class="count">Count</td>
        <th class="left">Original</td>
        <th class="right">Replacement</td>
        <th class="ignore">Ignore?</td>
      </tr>
      ${this.replacementCounts.map(c => this.renderReplacement(c))}
    </table>
    </div>
    `
    )}
     `;
  }

  renderReplacement(r: Replacement) {
    if (!r.count || r.count < 2) return;
    return html`
      <tr>
        <td class="count">${r.count}</td>
        <td class="left">${r.left}</td>
        <td class="right">${r.right}</td>
        <td class="ignore">
          <input type="checkbox" @change=${() => this.onIgnore(r)}></input>
        </td>
      </tr>
    `;
  }

  private onIgnore(r: Replacement) {
    this.getMagic().toggleIgnoredReplacement(r);
  }

  onFadedDiffClick() {
    const current = this.getMagic().getState().fadedDiff;
    this.getMagic().updateState({fadedDiff: !current});
  }

  onHideClick() {
    const hideBoth = !this.getMagic().getState().hideBoth;
    const hideControls = !this.getMagic().getState().hideControls;
    const hideHeaderRow = !this.getMagic().getState().hideHeaderRow;
    this.getMagic().updateState({hideBoth, hideControls, hideHeaderRow});
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

  onIgnoreInputChange() {
    if (!this.ignoreInput) return;
    this.getMagic().updateState({ignore: this.ignoreInput.value});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-magic': GrFileListMagic;
  }
}
