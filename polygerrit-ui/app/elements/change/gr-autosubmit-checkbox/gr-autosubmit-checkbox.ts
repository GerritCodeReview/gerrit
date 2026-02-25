
/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  LitElement,
  html,
  css,
  PropertyValues,
  state,
  property,
} from 'lit';
import {customElement} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {when} from 'lit/directives/when.js';

import '@material/web/checkbox/checkbox.js';
import {MdCheckbox} from '@material/web/checkbox/checkbox.js';
import '../../shared/gr-icon/gr-icon.js';
import '../../shared/gr-button/gr-button.js'; // For md-icon-button touch-target
import '@material/web/icon-button/icon-button.js';
import {sharedStyles} from '../../../styles/shared-styles.js';
import {materialStyles} from '../../../styles/gr-material-styles.js';

/** @typedef {import('../../shared/gr-icon/gr-icon.js').GrIcon} GrIcon */

/**
 * gr-autosubmit-checkbox is a component that displays an autosubmit checkbox or
 * an info message based on the provided properties.
 */
@customElement('gr-autosubmit-checkbox')
export class GrAutosubmitCheckbox extends LitElement {
  static override styles = [
    sharedStyles,
    materialStyles,
    css`
      :host {
        display: block;
      }
      .autosubmitContainer {
        display: flex;
        align-items: center;
        justify-content: center; /* Center content horizontally */
        margin-top: var(--spacing-m);
        margin-bottom: var(--spacing-m);
      }
      .autosubmit-info {
        display: flex;
        align-items: center;
        padding: var(--spacing-s) var(--spacing-m);
        border: 1px solid var(--info-foreground);
        border-radius: var(--border-radius);
        background-color: var(--info-background);
      }
      .autosubmit-info gr-icon {
        color: var(--info-foreground);
        margin-right: var(--spacing-m);
      }
      .autosubmit {
        display: flex;
        align-items: center;
        width: 100%;
      }
      .autosubmit-label {
        display: flex;
        align-items: center;
        cursor: pointer;
        width: 100%;
      }
      .autosubmit-text {
        padding-left: var(--spacing-m);
        flex-grow: 1;
      }
      .help {
        display: flex;
        align-items: center;
        margin-left: auto;
        text-decoration: none;
      }
      .help gr-icon {
        color: var(--deemphasized-text-color);
      }
      md-checkbox {
        --md-checkbox-container-size: 15px;
        --md-checkbox-icon-size: 15px;
      }
    `,
  ];

  /** Whether autosubmit is enabled for the current user/change. */
  @property({type: Boolean})
  isAutosubmitEnabled = false;

  /** Whether to show the autosubmit info message instead of the checkbox. */
  @property({type: Boolean})
  showAutosubmitInfoMessage = false;

  /** The current checked state of the autosubmit checkbox. */
  @property({
    type: Boolean,
  })
  autosubmitChecked = false;

  /** The URL for documentation related to autosubmit. */
  @property({
    type: String,
    attribute: 'docs-url',
  })
  docsUrl = '';

  override render() {
    return html`
      <div class="autosubmitContainer">
        ${when(
          this.showAutosubmitInfoMessage,
          () => this.renderInfoMessage(),
          () =>
            when(this.isAutosubmitEnabled, () => this.renderCheckbox())
        )}
      </div>
    `;
  }

  private renderInfoMessage() {
    return html`
      <div class="autosubmit-info">
        <gr-icon icon="info"></gr-icon>
        <span>Autosubmit Enabled.</span>
      </div>
    `;
  }

  private renderCheckbox() {
    return html`
      <div class="autosubmit">
        <label class="autosubmit-label">
          <md-checkbox
            id="autosubmit"
            ?checked=${this.autosubmitChecked}
            @change=${this.handleAutosubmitChanged}
          ></md-checkbox>
          <span class="autosubmit-text">Enable Autosubmit</span>
          ${this.renderDocumentationLink()}
        </label>
      </div>
    `;
  }

  private renderDocumentationLink() {
    if (!this.docsUrl) return nothing;
    return html`
      <a
        class="help"
        href=${this.docsUrl}
        target="_blank"
        rel="noopener noreferrer"
        tabindex="-1"
      >
        <md-icon-button touch-target="none" type="button">
          <gr-icon icon="help" title="read documentation"></gr-icon>
        </md-icon-button>
      </a>
    `;
  }

  private handleAutosubmitChanged(e: Event) {
    if (!(e.target instanceof MdCheckbox)) return;
    this.autosubmitChecked = e.target.checked;
    this.dispatchEvent(
      new CustomEvent<boolean>('autosubmit-checked', {
        detail: this.autosubmitChecked,
        composed: true,
        bubbles: true,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-autosubmit-checkbox': GrAutosubmitCheckbox;
  }

  interface HTMLElementEventMap {
    'autosubmit-checked': CustomEvent<boolean>;
  }
}
