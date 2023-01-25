/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {
  NumericChangeId,
  BranchName,
  ChangeActionDialog,
} from '../../../types/common';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-autocomplete/gr-autocomplete';
import {
  GrAutocomplete,
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {ValueChangedEvent} from '../../../types/events';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {KnownExperimentId} from '../../../services/flags/flags';

export interface RebaseChange {
  name: string;
  value: NumericChangeId;
}

export interface ConfirmRebaseEventDetail {
  base: string | null;
  allowConflicts: boolean;
  rebaseChain: boolean;
}

@customElement('gr-confirm-rebase-dialog')
export class GrConfirmRebaseDialog
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
  branch?: BranchName;

  @property({type: Number})
  changeNumber?: NumericChangeId;

  @property({type: Boolean})
  hasParent?: boolean;

  @property({type: Boolean})
  rebaseOnCurrent?: boolean;

  @property({type: Boolean})
  disableActions = false;

  @state()
  text = '';

  @state()
  shouldRebaseChain = false;

  @state()
  private query: AutocompleteQuery;

  @state()
  recentChanges?: RebaseChange[];

  @query('#rebaseOnParentInput')
  private rebaseOnParentInput!: HTMLInputElement;

  @query('#rebaseOnTipInput')
  private rebaseOnTipInput!: HTMLInputElement;

  @query('#rebaseOnOtherInput')
  rebaseOnOtherInput!: HTMLInputElement;

  @query('#rebaseAllowConflicts')
  private rebaseAllowConflicts!: HTMLInputElement;

  @query('#rebaseChain')
  private rebaseChain?: HTMLInputElement;

  @query('#parentInput')
  parentInput!: GrAutocomplete;

  private readonly restApiService = getAppContext().restApiService;

  private readonly flagsService = getAppContext().flagsService;

  constructor() {
    super();
    this.query = input => this.getChangeSuggestions(input);
  }

  override willUpdate(changedProperties: PropertyValues): void {
    if (
      changedProperties.has('rebaseOnCurrent') ||
      changedProperties.has('hasParent')
    ) {
      this.updateSelectedOption();
    }
  }

  static override styles = [
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
      .message {
        font-style: italic;
      }
      .parentRevisionContainer label,
      .parentRevisionContainer input[type='text'] {
        display: block;
        width: 100%;
      }
      .rebaseAllowConflicts {
        margin-top: var(--spacing-m);
      }
      .rebaseOption {
        margin: var(--spacing-m) 0;
      }
    `,
  ];

  override render() {
    return html`
      <gr-dialog
        id="confirmDialog"
        confirm-label="Rebase"
        .disabled=${this.disableActions}
        @confirm=${this.handleConfirmTap}
        @cancel=${this.handleCancelTap}
      >
        <div class="header" slot="header">Confirm rebase</div>
        <div class="main" slot="main">
          <div
            id="rebaseOnParent"
            class="rebaseOption"
            ?hidden=${!this.displayParentOption()}
          >
            <input id="rebaseOnParentInput" name="rebaseOptions" type="radio" />
            <label id="rebaseOnParentLabel" for="rebaseOnParentInput">
              Rebase on parent change
            </label>
          </div>
          <div
            id="parentUpToDateMsg"
            class="message"
            ?hidden=${!this.displayParentUpToDateMsg()}
          >
            This change is up to date with its parent.
          </div>
          <div
            id="rebaseOnTip"
            class="rebaseOption"
            ?hidden=${!this.displayTipOption()}
          >
            <input
              id="rebaseOnTipInput"
              name="rebaseOptions"
              type="radio"
              ?disabled=${!this.displayTipOption()}
            />
            <label id="rebaseOnTipLabel" for="rebaseOnTipInput">
              Rebase on top of the ${this.branch} branch<span
                ?hidden=${!this.hasParent || this.shouldRebaseChain}
              >
                (breaks relation chain)
              </span>
            </label>
          </div>
          <div
            id="tipUpToDateMsg"
            class="message"
            ?hidden=${this.displayTipOption()}
          >
            Change is up to date with the target branch already (${this.branch})
          </div>
          <div id="rebaseOnOther" class="rebaseOption">
            <input
              id="rebaseOnOtherInput"
              name="rebaseOptions"
              type="radio"
              @click=${this.handleRebaseOnOther}
            />
            <label id="rebaseOnOtherLabel" for="rebaseOnOtherInput">
              Rebase on a specific change, ref, or commit
              <span ?hidden=${!this.hasParent || this.shouldRebaseChain}>
                (breaks relation chain)
              </span>
            </label>
          </div>
          <div class="parentRevisionContainer">
            <gr-autocomplete
              id="parentInput"
              .query=${this.query}
              no-debounce
              .text=${this.text}
              @text-changed=${(e: ValueChangedEvent) =>
                (this.text = e.detail.value)}
              @click=${this.handleEnterChangeNumberClick}
              allow-non-suggested-values
              placeholder="Change number, ref, or commit hash"
            >
            </gr-autocomplete>
          </div>
          <div class="rebaseAllowConflicts">
            <input id="rebaseAllowConflicts" type="checkbox" />
            <label for="rebaseAllowConflicts"
              >Allow rebase with conflicts</label
            >
          </div>
          ${when(
            this.flagsService.isEnabled(KnownExperimentId.REBASE_CHAIN) &&
              this.hasParent,
            () =>
              html`<div>
                <input
                  id="rebaseChain"
                  type="checkbox"
                  @change=${() => {
                    this.shouldRebaseChain = !!this.rebaseChain?.checked;
                  }}
                />
                <label for="rebaseChain">Rebase all ancestors</label>
              </div>`
          )}
        </div>
      </gr-dialog>
    `;
  }

  // This is called by gr-change-actions every time the rebase dialog is
  // re-opened. Unlike other autocompletes that make a request with each
  // updated input, this one gets all recent changes once and then filters
  // them by the input. The query is re-run each time the dialog is opened
  // in case there are new/updated changes in the generic query since the
  // last time it was run.
  fetchRecentChanges() {
    return this.restApiService
      .getChanges(
        undefined,
        'is:open -age:90d',
        /* offset=*/ undefined,
        /* options=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        if (!response) return [];
        const changes: RebaseChange[] = [];
        for (const change of response) {
          changes.push({
            name: `${change._number}: ${change.subject}`,
            value: change._number,
          });
        }
        this.recentChanges = changes;
        return this.recentChanges;
      });
  }

  getRecentChanges() {
    if (this.recentChanges) {
      return Promise.resolve(this.recentChanges);
    }
    return this.fetchRecentChanges();
  }

  private getChangeSuggestions(input: string) {
    return this.getRecentChanges().then(changes =>
      this.filterChanges(input, changes)
    );
  }

  filterChanges(
    input: string,
    changes: RebaseChange[]
  ): AutocompleteSuggestion[] {
    return changes
      .filter(
        change =>
          change.name.includes(input) && change.value !== this.changeNumber
      )
      .map(
        change =>
          ({
            name: change.name,
            value: `${change.value}`,
          } as AutocompleteSuggestion)
      );
  }

  private displayParentOption() {
    return this.hasParent && this.rebaseOnCurrent;
  }

  private displayParentUpToDateMsg() {
    return this.hasParent && !this.rebaseOnCurrent;
  }

  private displayTipOption() {
    return this.rebaseOnCurrent || this.hasParent;
  }

  /**
   * There is a subtle but important difference between setting the base to an
   * empty string and omitting it entirely from the payload. An empty string
   * implies that the parent should be cleared and the change should be
   * rebased on top of the target branch. Leaving out the base implies that it
   * should be rebased on top of its current parent.
   */
  getSelectedBase() {
    if (this.rebaseOnParentInput.checked) {
      return null;
    }
    if (this.rebaseOnTipInput.checked) {
      return '';
    }
    if (!this.text) {
      return '';
    }
    // Change numbers will have their description appended by the
    // autocomplete.
    return this.text.split(':')[0];
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    const detail: ConfirmRebaseEventDetail = {
      base: this.getSelectedBase(),
      allowConflicts: this.rebaseAllowConflicts.checked,
      rebaseChain: !!this.rebaseChain?.checked,
    };
    this.dispatchEvent(new CustomEvent('confirm', {detail}));
    this.text = '';
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel'));
    this.text = '';
  }

  private handleRebaseOnOther() {
    this.parentInput.focus();
  }

  private handleEnterChangeNumberClick() {
    this.rebaseOnOtherInput.checked = true;
  }

  /**
   * Sets the default radio button based on the state of the app and
   * the corresponding value to be submitted.
   */
  private updateSelectedOption() {
    const {rebaseOnCurrent, hasParent} = this;
    if (rebaseOnCurrent === undefined || hasParent === undefined) {
      return;
    }

    if (this.displayParentOption()) {
      this.rebaseOnParentInput.checked = true;
    } else if (this.displayTipOption()) {
      this.rebaseOnTipInput.checked = true;
    } else {
      this.rebaseOnOtherInput.checked = true;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-rebase-dialog': GrConfirmRebaseDialog;
  }
}
