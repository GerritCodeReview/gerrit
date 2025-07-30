/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../shared/gr-dialog/gr-dialog';
import {Key, Modifier} from '../../../utils/dom-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {ShortcutController} from '../../lit/shortcut-controller';
import {ChangeActionDialog} from '../../../types/common';
import {fireNoBubble} from '../../../utils/event-util';
import {formStyles} from '../../../styles/form-styles';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-abandon-dialog': GrConfirmAbandonDialog;
  }
}

@customElement('gr-confirm-abandon-dialog')
export class GrConfirmAbandonDialog
  extends LitElement
  implements ChangeActionDialog
{
  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  @query('#messageInput') private messageInput?: GrAutogrowTextarea;

  @property({type: String})
  message = '';

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    this.shortcuts.addLocal(
      {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]},
      () => this.confirm()
    );

    this.shortcuts.addLocal(
      {key: Key.ENTER, modifiers: [Modifier.META_KEY]},
      _ => this.confirm()
    );
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      css`
        :host {
          display: block;
        }
        :host([disabled]) {
          opacity: 0.5;
          pointer-events: none;
        }
        .main {
          display: flex;
          flex-direction: column;
          width: 100%;
        }
        label {
          cursor: pointer;
          display: block;
          width: 100%;
        }
        gr-autogrow-textarea {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 73ch; /* Add a char to account for the border. */
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-dialog
        confirm-label="Abandon"
        @confirm=${(e: Event) => {
          this.handleConfirmTap(e);
        }}
        @cancel=${(e: Event) => {
          this.handleCancelTap(e);
        }}
      >
        <div class="header" slot="header">Abandon Change</div>
        <div class="main" slot="main">
          <label for="messageInput">Abandon Message</label>
          <gr-autogrow-textarea
            id="messageInput"
            class="message"
            autocomplete="on"
            placeholder="&lt;Insert reasoning here&gt;"
            .value=${this.message}
            @input=${this.handleInputChanged}
          ></gr-autogrow-textarea>
        </div>
      </gr-dialog>
    `;
  }

  resetFocus() {
    assertIsDefined(this.messageInput, 'messageInput');
    this.messageInput.focus();
  }

  // private but used in test
  handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.confirm();
  }

  // private but used in test
  confirm() {
    fireNoBubble(this, 'confirm', {});
  }

  // private but used in test
  handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubble(this, 'cancel', {});
  }

  private handleInputChanged(e: InputEvent) {
    const value = (e.target as GrAutogrowTextarea).value ?? '';
    this.message = value;
  }
}
