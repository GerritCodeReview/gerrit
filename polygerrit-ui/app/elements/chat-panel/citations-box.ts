/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';

import {chatModelToken, Turn} from '../../models/chat/chat-model';
import {resolve} from '../../models/dependency';
import {subscribe} from '../lit/subscription-controller';

@customElement('citations-box')
export class CitationsBox extends LitElement {
  private readonly getChatModel = resolve(this, chatModelToken);

  @property({type: Number}) turnIndex = 0;

  @state() turns: readonly Turn[] = [];

  @state() citation_url?: string;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChatModel().turns$,
      turns => (this.turns = turns ?? [])
    );
    subscribe(
      this,
      () => this.getChatModel().models$,
      models => (this.citation_url = models?.citation_url)
    );
  }

  static override styles = [
    css`
      :host {
        .citations-display-box {
          display: block;
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: var(--spacing-s) var(--spacing-xl) var(--spacing-xl)
            var(--spacing-xl);
          margin-top: var(--spacing-xl);
          margin-bottom: var(--spacing-xl);
          background-color: var(--background-color-tertiary);
        }
        .citations-summary-message {
          font-size: var(--font-size-small);
          line-height: var(--line-height-small);
          font-weight: var(--font-weight-medium);
          letter-spacing: 0;
          color: var(--primary-text-color);
          margin-bottom: var(--spacing-m);
        }
        .citation-entry-list {
          list-style-type: disc;
          padding-left: 20px;
          margin-top: 0;
          margin-bottom: 0;
        }
        li {
          font-size: var(--font-size-small);
          line-height: var(--line-height-small);
          font-weight: var(--font-weight-normal);
          letter-spacing: 0;
          color: var(--deemphasized-text-color);
          overflow-wrap: break-word; /* Prevent long unbreakable strings from overflowing */
          margin-bottom: var(--spacing-xs);
        }
        li:last-child {
          margin-bottom: 0;
        }
        a {
          color: var(--link-color);
          text-decoration: none;
        }
        a:hover {
          text-decoration: underline;
        }
      }
    `,
  ];

  override render() {
    if (!this.citation_url) return;
    const citations =
      this.turns[this.turnIndex]?.geminiMessage?.citations ?? [];

    if (citations.length === 0) return;
    const count = citations.length;

    return html`
      <div class="citations-display-box">
        <p class="citations-summary-message">
          Use
          <a
            href=${this.citation_url}
            target="_blank"
            rel="noopener noreferrer"
          >
            with caution</a
          >
          . The model answer includes ${count} citation${count > 1 ? 's' : ''}
          from other sources:
        </p>
        <ul class="citation-entry-list">
          ${citations.map(
            citationUrl => html`
              <li class="citation-item">
                <a
                  .href=${citationUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  >${citationUrl}</a
                >
              </li>
            `
          )}
        </ul>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'citations-box': CitationsBox;
  }
}
