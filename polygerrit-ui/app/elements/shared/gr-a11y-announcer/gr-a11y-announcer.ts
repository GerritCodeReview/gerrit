/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';

/**
 * `gr-a11y-announcer` is a singleton element that allows screen reader
 * announcements via dispatching a bubbling `iron-announce` event with a
 * `text` detail payload.
 */
@customElement('gr-a11y-announcer')
export class GrA11yAnnouncer extends LitElement {
  /**
   * The `aria-live` mode: 'off', 'polite', or 'assertive'.
   */
  @property({type: String}) mode: 'off' | 'polite' | 'assertive' = 'polite';

  /**
   * Time (ms) to wait before setting the text, to ensure screen readers re-announce.
   */
  @property({type: Number}) timeout = 150;

  @state()
  private _text = '';

  static instance: GrA11yAnnouncer | null = null;

  static override styles = css`
    :host {
      display: inline-block;
      position: fixed;
      clip: rect(0, 0, 0, 0);
    }
  `;

  override connectedCallback() {
    super.connectedCallback();

    if (!GrA11yAnnouncer.instance) {
      GrA11yAnnouncer.instance = this;
    }

    document.addEventListener('iron-announce', this._onIronAnnounce);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    document.removeEventListener('iron-announce', this._onIronAnnounce);
  }

  override render() {
    return html`<div aria-live=${this.mode}>${this._text}</div>`;
  }

  private _onIronAnnounce = (event: Event) => {
    const customEvent = event as CustomEvent<{text?: string}>;
    if (customEvent.detail?.text) {
      this.announce(customEvent.detail.text);
    }
  };

  /**
   * Causes the given text to be announced by screen readers.
   */
  announce(text: string) {
    // Clear first to allow repeated announcements of the same text.
    this._text = '';
    setTimeout(() => {
      this._text = text;
    }, this.timeout);
  }

  /**
   * Ensures the singleton announcer element is available in the document.
   */
  static requestAvailability(): GrA11yAnnouncer {
    if (!GrA11yAnnouncer.instance) {
      const announcer = document.createElement(
        'gr-a11y-announcer'
      ) as GrA11yAnnouncer;
      document.body.appendChild(announcer);
      GrA11yAnnouncer.instance = announcer;
    }
    return GrA11yAnnouncer.instance;
  }
}
