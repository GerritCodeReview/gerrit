/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {BranchName, ChangeActionDialog, RepoName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import {Key, Modifier} from '../../../utils/dom-util';
import {ValueChangedEvent} from '../../../types/events';
import {ShortcutController} from '../../lit/shortcut-controller';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {fireNoBubble} from '../../../utils/event-util';
import {formStyles} from '../../../styles/form-styles';
import {branchName} from '../../../utils/patch-set-util';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';

const SUGGESTIONS_LIMIT = 15;

@customElement('gr-confirm-move-dialog')
export class GrConfirmMoveDialog
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

  @property({type: String})
  branch = '' as BranchName;

  @property({type: String})
  message = '';

  @property({type: String})
  project?: RepoName;

  private readonly shortcuts = new ShortcutController(this);

  constructor() {
    super();
    this.shortcuts.addLocal(
      {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]},
      e => this.handleConfirmTap(e)
    );
    this.shortcuts.addLocal(
      {key: Key.ENTER, modifiers: [Modifier.META_KEY]},
      e => this.handleConfirmTap(e)
    );
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
  }

  override connectedCallback() {
    super.connectedCallback();
  }

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      formStyles,
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
            @text-changed=${(e: ValueChangedEvent) =>
              (this.branch = e.detail.value as BranchName)}
            .query=${(text: string) => this.getProjectBranchesSuggestions(text)}
            placeholder="Destination branch"
          >
          </gr-autocomplete>
          <label for="messageInput"> Move Change Message </label>
          <gr-autogrow-textarea
            id="messageInput"
            class="message"
            autocomplete="on"
            .rows=${4}
            .maxRows=${15}
            .value=${this.message}
            @input=${(e: InputEvent) => {
              const value = (e.target as GrAutogrowTextarea).value ?? '';
              this.message = value;
            }}
          ></gr-autogrow-textarea>
        </div>
      </gr-dialog>
    `;
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubble(this, 'confirm', {});
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubble(this, 'cancel', {});
  }

  private getProjectBranchesSuggestions(input: string) {
    if (!this.project) return Promise.reject(new Error('Missing project'));
    return this.restApiService
      .getRepoBranches(
        branchName(input),
        this.project,
        SUGGESTIONS_LIMIT,
        /* offest=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          branches.push({name: branchName(branchInfo.ref)});
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
