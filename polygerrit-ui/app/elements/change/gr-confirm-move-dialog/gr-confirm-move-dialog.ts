/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {BranchName, RepoName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import {addShortcut, Key, Modifier} from '../../../utils/dom-util';

const SUGGESTIONS_LIMIT = 15;

@customElement('gr-confirm-move-dialog')
export class GrConfirmMoveDialog extends LitElement {
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

  @property({type: String})
  branch = '' as BranchName;

  @property({type: String})
  message = '';

  @property({type: String})
  project?: RepoName;

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
      addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]}, e =>
        this.handleConfirmTap(e)
      )
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.META_KEY]}, e =>
        this.handleConfirmTap(e)
      )
    );
  }

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          width: 30em;
        }
        :host([disabled]) {
          opacity: 0.5;
          pointer-events: none;
        }
        label {
          cursor: pointer;
        }
        .main {
          display: flex;
          flex-direction: column;
          width: 100%;
        }
        .main label,
        .main input[type='text'] {
          display: block;
          width: 100%;
        }
        .main .message {
          width: 100%;
        }
        .warning {
          color: var(--error-text-color);
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-dialog
        confirm-label="Move Change"
        @confirm=${(e: Event) => this.handleConfirmTap(e)}
        @cancel=${(e: Event) => this.handleCancelTap(e)}
      >
        <div class="header" slot="header">Move Change to Another Branch</div>
        <div class="main" slot="main">
          <p class="warning">
            Warning: moving a change will not change its parents.
          </p>
          <label for="branchInput"> Move change to branch </label>
          <gr-autocomplete
            id="branchInput"
            .text=${this.branch}
            .query=${(text: string) => this.getProjectBranchesSuggestions(text)}
            placeholder="Destination branch"
          >
          </gr-autocomplete>
          <label for="messageInput"> Move Change Message </label>
          <iron-autogrow-textarea
            id="messageInput"
            class="message"
            autocomplete="on"
            .rows=${4}
            .maxRows=${15}
            .bindValue=${this.message}
          ></iron-autogrow-textarea>
        </div>
      </gr-dialog>
    `;
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
  }

  private getProjectBranchesSuggestions(input: string) {
    if (!this.project) return Promise.reject(new Error('Missing project'));
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.restApiService
      .getRepoBranches(input, this.project, SUGGESTIONS_LIMIT)
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          let name: string = branchInfo.ref;
          if (name.startsWith('refs/heads/')) {
            name = name.substring('refs/heads/'.length);
          }
          branches.push({name: name as BranchName});
        }
        return branches;
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-move-dialog': GrConfirmMoveDialog;
  }
}
