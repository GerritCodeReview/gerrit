/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement} from 'lit/decorators.js';
import {getAppContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
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
export class GrUserSuggetionFix extends LitElement {
  private readonly flagsService = getAppContext().flagsService;

  static override styles = [
    css`
      .header {
        background-color: var(--user-suggestion-header-background);
        color: var(--user-suggestion-header-color);
        border: 1px solid var(--border-color);
        border-bottom: 0;
        padding: var(--spacing-xs) var(--spacing-s);
        display: flex;
        align-items: center;
      }
      .header .title {
        flex: 1;
      }
      gr-copy-clipboard {
        --gr-copy-clipboard-icon-color: var(--user-suggestion-header-color);
      }
      code {
        max-width: var(--gr-formatted-text-prose-max-width, none);
        background-color: var(--background-color-secondary);
        border: 1px solid var(--border-color);
        border-top: 0;
        display: block;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-mono);
        margin-bottom: var(--spacing-m);
        padding: var(--spacing-xxs) var(--spacing-s);
        overflow-x: auto;
        /* Pre will preserve whitespace and line breaks but not wrap */
        white-space: pre;
      }
    `,
  ];

  override render() {
    if (!this.flagsService.isEnabled(KnownExperimentId.SUGGEST_EDIT)) {
      return nothing;
    }
    if (!this.textContent) return nothing;
    const code = this.textContent;
    return html`<div class="header">
        <div class="title">Suggested fix</div>
        <div>
          <gr-copy-clipboard
            hideInput=""
            text=${code}
            copyTargetName="Suggested fix"
          ></gr-copy-clipboard>
        </div>
        <div>
          <gr-button
            secondary
            class="action show-fix"
            @click=${this.handleShowFix}
          >
            Preview Fix
          </gr-button>
        </div>
      </div>
      <code>${code}</code>`;
  }

  handleShowFix() {
    if (!this.textContent) return;
    fire(this, 'open-user-suggest-preview', {code: this.textContent});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-suggestion-fix': GrUserSuggetionFix;
  }
}
