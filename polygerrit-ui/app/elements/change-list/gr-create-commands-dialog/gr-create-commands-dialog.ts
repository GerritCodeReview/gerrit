/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-shell-command/gr-shell-command';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {modalStyles} from '../../../styles/gr-modal-styles';

enum Commands {
  CREATE = 'git commit',
  AMEND = 'git commit --amend',
  PUSH_PREFIX = 'git push origin HEAD:refs/for/',
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-commands-dialog': GrCreateCommandsDialog;
  }
}

@customElement('gr-create-commands-dialog')
export class GrCreateCommandsDialog extends LitElement {
  @query('#commandsModal')
  commandsModal?: HTMLDialogElement;

  @property({type: String})
  branch?: string;

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      css`
        ol {
          list-style: decimal;
          margin-left: var(--spacing-l);
        }
        p {
          margin-bottom: var(--spacing-m);
        }
        #commandsDialog {
          max-width: 40em;
        }
      `,
    ];
  }

  override render() {
    return html` <dialog id="commandsModal" tabindex="-1">
      <gr-dialog
        id="commandsDialog"
        confirm-label="Done"
        cancel-label=""
        confirm-on-enter=""
        @confirm=${() => this.commandsModal?.close()}
      >
        <div class="header" slot="header">Create change commands</div>
        <div class="main" slot="main">
          <ol>
            <li>
              <p>Make the changes to the files on your machine</p>
            </li>
            <li>
              <p>If you are making a new commit use</p>
              <gr-shell-command .command=${Commands.CREATE}></gr-shell-command>
              <p>Or to amend an existing commit use</p>
              <gr-shell-command .command=${Commands.AMEND}></gr-shell-command>
              <p>
                Please make sure you add a commit message as it becomes the
                description for your change.
              </p>
            </li>
            <li>
              <p>Push the change for code review</p>
              <gr-shell-command
                .command=${Commands.PUSH_PREFIX + (this.branch ?? '[BRANCH]')}
              ></gr-shell-command>
            </li>
            <li>
              <p>
                Close this dialog and you should be able to see your recently
                created change in the 'Outgoing changes' section on the 'Your
                changes' page.
              </p>
            </li>
          </ol>
        </div>
      </gr-dialog>
    </dialog>`;
  }

  open() {
    this.commandsModal?.showModal();
  }
}
