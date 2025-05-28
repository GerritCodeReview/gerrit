/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-button/gr-button';
import '../../change/gr-copy-links/gr-copy-links';
import {CopyLink, GrCopyLinks} from '../../change/gr-copy-links/gr-copy-links';
import {prependOrigin} from '../../../utils/url-util';
import {ChangeInfo} from '../../../api/rest-api';

@customElement('gr-change-list-copy-link-flow')
export class GrChangeListCopyLinkFlow extends LitElement {
  @query('gr-copy-links')
  private copyLinks?: GrCopyLinks;

  static override get styles() {
    return css`
      :host {
        display: inline-block;
        position: relative;
      }
      gr-button {
        margin-right: var(--spacing-s);
      }
    `;
  }

  @state() private selectedChangeNums: number[] = [];

  @state() private selectedChanges: ChangeInfo[] = [];

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => (this.selectedChangeNums = selectedChangeNums)
    );
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChanges$,
      selectedChanges => (this.selectedChanges = selectedChanges)
    );
  }

  // private but used in test
  getCopyLinks(): CopyLink[] {
    if (this.selectedChangeNums.length === 0) return [];
    const changeIds = this.selectedChangeNums
      .map(num => `change:${num}`)
      .join('+OR+');
    const url = prependOrigin(`/q/${changeIds}`);
    return [
      {
        label: 'Change Query URL',
        shortcut: 'u',
        value: url,
      },
      {
        label: 'Markdown',
        shortcut: 'm',
        value: this.selectedChanges
          .map(change => {
            const changeUrl = prependOrigin(`/c/${change._number}`);
            return `[${change.subject}](${changeUrl})`;
          })
          .join('\n'),
        multiline: true,
      },
    ];
  }

  override render() {
    if (this.selectedChangeNums.length === 0) return nothing;
    return html`
      <gr-button
        id="copyLinkButton"
        link
        @click=${() => {
          this.copyLinks?.openDropdown();
        }}
      >
        Copy Link
      </gr-button>
      <gr-copy-links
        .copyLinks=${this.getCopyLinks()}
        .horizontalAlign=${'right'}
        .shortcutPrefix=${''}
      ></gr-copy-links>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-copy-link-flow': GrChangeListCopyLinkFlow;
  }
}
