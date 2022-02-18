/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
 import '../../shared/gr-dialog/gr-dialog';
 import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
 import {addShortcut, Key, Modifier} from '../../../utils/dom-util';
 import {sharedStyles} from '../../../styles/shared-styles';
 import {LitElement, html, css} from 'lit';
 import {customElement, property, query} from 'lit/decorators';
 import {assertIsDefined} from '../../../utils/common-util';
 import {BindValueChangeEvent} from '../../../types/events';
 
 export interface GrConfirmAbandonDialog {
   $: {
     messageInput: IronAutogrowTextareaElement;
   };
 }
 
 declare global {
   interface HTMLElementTagNameMap {
     'gr-confirm-abandon-dialog': GrConfirmAbandonDialog;
   }
 }
 
 @customElement('gr-confirm-abandon-dialog')
 export class GrConfirmAbandonDialog extends LitElement {
 
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
  
   @query('#messageInput') private messageInput?: IronAutogrowTextareaElement;

   @property({type: String})
   message = '';
 
   /** Called in disconnectedCallback. */
   private cleanups: (() => void)[] = [];
 
   override disconnectedCallback() {
     super.disconnectedCallback();
     for (const cleanup of this.cleanups) cleanup();
     this.cleanups = [];
   }
 
   override connectedCallback() {
     super.connectedCallback();
     this.cleanups.push(
       addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]}, _ =>
         this.confirm()
       )
     );
     this.cleanups.push(
       addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.META_KEY]}, _ =>
         this.confirm()
       )
     );
   }

   static override get styles() {
    return [
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
        iron-autogrow-textarea {
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
      @confirm=${(e: Event) => { this.handleConfirmTap(e); }}
      @cancel=${(e: Event) => { this.handleCancelTap(e); }}
      >
        <div class="header" slot="header">Abandon Change</div>
        <div class="main" slot="main">
          <label for="messageInput">Abandon Message</label>
          <iron-autogrow-textarea
            id="messageInput"
            class="message"
            autocomplete="on"
            placeholder="<Insert reasoning here>"
            .bindValue=${this.message}
            @bind-value-changed=${(e: BindValueChangeEvent) => { this.handleBindValueChanged(e); }}
          ></iron-autogrow-textarea>
        </div>
      </gr-dialog>
    `;
  }
 
   resetFocus() {
    assertIsDefined(this.messageInput, 'messageInput');
     this.messageInput.textarea.focus();
   }
 
   // private but used in test
   handleConfirmTap(e: Event) {
     e.preventDefault();
     e.stopPropagation();
     this.confirm();
   }
 
   private confirm() {
     this.dispatchEvent(
       new CustomEvent('confirm', {
         detail: {reason: this.message},
         composed: true,
         bubbles: false,
       })
     );
   }
 
   // private but used in test
   handleCancelTap(e: Event) {
     e.preventDefault();
     e.stopPropagation();
     this.dispatchEvent(
       new CustomEvent('cancel', {
         composed: true,
         bubbles: false,
       })
     );
   }

   private handleBindValueChanged(e: BindValueChangeEvent) {
     this.message = e.detail.value;
   }
 }
 