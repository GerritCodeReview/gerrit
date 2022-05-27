/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-shell-command/gr-shell-command';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property, query} from 'lit/decorators';

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
  @query('#commandsOverlay')
  commandsOverlay?: GrOverlay;

  @property({type: String})
  branch?: string;

  static override get styles() {
    return [
      sharedStyles,
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
    return html` <gr-overlay id="commandsOverlay" with-backdrop="">
      <gr-dialog
        id="commandsDialog"
        confirm-label="Done"
        cancel-label=""
        confirm-on-enter=""
        @confirm=${() => this.commandsOverlay?.close()}
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
    </gr-overlay>`;
  }

  open() {
    this.commandsOverlay?.open();
  }
}
