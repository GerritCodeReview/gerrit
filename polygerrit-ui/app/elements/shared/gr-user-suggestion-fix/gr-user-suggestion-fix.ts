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
          border-bottom-left-radius: var(--border-radius);
          border-bottom-right-radius: var(--border-radius);
        }
      `,
    ];
  }

  override render() {
    if (!this.flagsService.isEnabled(KnownExperimentId.SUGGEST_EDIT)) {
      return nothing;
    }
    if (!this.textContent) return nothing;
    const code = this.textContent;
    return html`<div class="header">
        <div class="title">
          <span>Suggested edit</span>
          <a
            href="https://gerrit-review.googlesource.com/Documentation/user-suggest-edits.html"
            target="_blank"
            ><gr-icon icon="help" title="read documentation"></gr-icon
          ></a>
        </div>
        <div class="copyButton">
          <gr-copy-clipboard
            hideInput=""
            text=${code}
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
