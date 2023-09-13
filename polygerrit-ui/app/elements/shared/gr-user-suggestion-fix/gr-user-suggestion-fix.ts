/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {css, html, LitElement, nothing} from 'lit';
import {customElement} from 'lit/decorators.js';
import {fire} from '../../../utils/event-util';

declare global {
  interface HTMLElementEventMap {
    'open-user-suggest-preview': OpenUserSuggestionPreviewEvent;
  }
}

export type OpenUserSuggestionPreviewEvent =
  CustomEvent<OpenUserSuggestionPreviewEventDetail>;
export interface OpenUserSuggestionPreviewEventDetail {
  code: string;
}

@customElement('gr-user-suggestion-fix')
export class GrUserSuggestionsFix extends LitElement {
  constructor() {
    super();
  }

  static override get styles() {
    return [
      css`
        .header {
          background-color: var(--background-color-primary);
          border: 1px solid var(--border-color);
          padding: var(--spacing-xs) var(--spacing-xl);
          display: flex;
          align-items: center;
          border-top-left-radius: var(--border-radius);
          border-top-right-radius: var(--border-radius);
        }
        .header .title {
          flex: 1;
        }
        .copyButton {
          margin-right: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    if (!this.textContent) return nothing;
    const code = this.textContent;
    return html`<div class="header">
        <div class="title">
          <span>Suggested edit</span>
          <a
            href="https://gerrit-review.googlesource.com/Documentation/user-suggest-edits.html"
            target="_blank"
            rel="noopener noreferrer"
            ><gr-icon icon="help" title="read documentation"></gr-icon
          ></a>
        </div>
        <div class="copyButton">
          <gr-copy-clipboard
            hideInput=""
            text=${code}
            multiline
            copyTargetName="Suggested edit"
          ></gr-copy-clipboard>
        </div>
        <div>
          <gr-button
            secondary
            flatten
            class="action show-fix"
            @click=${this.handleShowFix}
          >
            Show edit
          </gr-button>
        </div>
      </div>
      <gr-suggestion-diff-preview
        .suggestion=${this.textContent}
      ></gr-suggestion-diff-preview>`;
  }

  handleShowFix() {
    if (!this.textContent) return;
    fire(this, 'open-user-suggest-preview', {code: this.textContent});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-suggestion-fix': GrUserSuggestionsFix;
  }
}
