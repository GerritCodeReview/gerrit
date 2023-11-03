/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-account-chip/gr-account-chip';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {
  AccountDetailInfo,
  AccountInfo,
  BranchName,
  ChangeActionDialog,
  EmailInfo,
  NumericChangeId,
  GitPersonInfo,
} from '../../../types/common';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-autocomplete/gr-autocomplete';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {ValueChangedEvent} from '../../../types/events';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {userModelToken} from '../../../models/user/user-model';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';
import {subscribe} from '../../lit/subscription-controller';
import {formStyles} from '../../../styles/form-styles';

export interface RebaseChange {
  name: string;
  value: NumericChangeId;
}

export interface ConfirmRebaseEventDetail {
  base: string | null;
  allowConflicts: boolean;
  rebaseChain: boolean;
  onBehalfOfUploader: boolean;
  committerEmail: string | null;
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

  @property({type: Boolean})
  rebaseOnCurrent?: boolean;

  @property({type: Boolean})
  disableActions = false;

  @state()
  changeNum?: NumericChangeId;

  @state()
  hasParent?: boolean;

  @state()
  text = '';

  @state()
  shouldRebaseChain = false;

  @state()
  private query: AutocompleteQuery;

  @state()
  recentChanges?: RebaseChange[];

  @state()
  allowConflicts = false;

  @state()
  selectedEmailForRebase: string | null | undefined;

  @state()
  currentUserEmails: EmailInfo[] = [];

  @state()
  uploaderEmails: EmailInfo[] = [];

  @state()
  committerEmailDropdownItems: EmailInfo[] = [];

  @query('#rebaseOnParentInput')
  private rebaseOnParentInput?: HTMLInputElement;

  @query('#rebaseOnTipInput')
  private rebaseOnTipInput?: HTMLInputElement;

  @query('#rebaseOnOtherInput')
  rebaseOnOtherInput?: HTMLInputElement;

  @query('#rebaseAllowConflicts')
  private rebaseAllowConflicts?: HTMLInputElement;

  @query('#rebaseChain')
  private rebaseChain?: HTMLInputElement;

  @query('#parentInput')
  parentInput!: GrAutocomplete;

  @state()
  account?: AccountDetailInfo;

  @state()
  uploader?: AccountInfo;

  @state()
  latestCommitter?: GitPersonInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getRelatedChangesModel = resolve(
    this,
    relatedChangesModelToken
  );

  constructor() {
    super();
    this.query = input => this.getChangeSuggestions(input);
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.account = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestUploader$,
      x => (this.uploader = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.changeNum = x)
    );
    subscribe(
      this,
      () => this.getRelatedChangesModel().hasParent$,
      x => (this.hasParent = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestCommitter$,
      x => (this.latestCommitter = x)
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadCommitterEmailDropdownItems();
  }

  override willUpdate(changedProperties: PropertyValues): void {
    if (
      changedProperties.has('rebaseOnCurrent') ||
      changedProperties.has('hasParent')
    ) {
      this.updateSelectedOption();
    }
  }

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
        .message {
          font-style: italic;
        }
        .parentRevisionContainer label,
        .parentRevisionContainer input[type='text'] {
          display: block;
          width: 100%;
        }
        .rebaseCheckbox {
          margin-top: var(--spacing-m);
        }
        .rebaseOption {
          margin: var(--spacing-m) 0;
        }
        .rebaseOnBehalfMsg {
          margin-top: var(--spacing-m);
        }
        .rebaseWithCommitterEmail {
          margin-top: var(--spacing-m);
        }
      `,
    ];
  }

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
          <div class="message" ?hidden=${this.hasParent !== undefined}>
            Still loading parent information ...
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
              .text=${this.text}
              @text-changed=${(e: ValueChangedEvent) =>
                (this.text = e.detail.value)}
              @click=${this.handleEnterChangeNumberClick}
              allow-non-suggested-values
              placeholder="Change number, ref, or commit hash"
            >
            </gr-autocomplete>
          </div>
          <div class="rebaseCheckbox">
            <input
              id="rebaseAllowConflicts"
              type="checkbox"
              @change=${() => {
                this.allowConflicts = !!this.rebaseAllowConflicts?.checked;
                this.loadCommitterEmailDropdownItems();
              }}
            />
            <label for="rebaseAllowConflicts"
              >Allow rebase with conflicts</label
            >
          </div>
          ${when(
            !this.isCurrentUserEqualToLatestUploader() && this.allowConflicts,
            () =>
              html`<span class="message"
                >Rebase cannot be done on behalf of the uploader when allowing
                conflicts.</span
              >`
          )}
          ${when(
            this.hasParent,
            () =>
              html`<div class="rebaseCheckbox">
                <input
                  id="rebaseChain"
                  type="checkbox"
                  @change=${() => {
                    this.shouldRebaseChain = !!this.rebaseChain?.checked;
                    if (this.shouldRebaseChain) {
                      this.selectedEmailForRebase = undefined;
                    }
                  }}
                />
                <label for="rebaseChain">Rebase all ancestors</label>
              </div>`
          )}
          ${when(
            !this.isCurrentUserEqualToLatestUploader(),
            () => html`<div class="rebaseOnBehalfMsg">Rebase will be done on behalf of${
              !this.allowConflicts ? ' the uploader:' : ''
            } <gr-account-chip
                .account=${this.allowConflicts ? this.account : this.uploader}
              ></gr-account-chip
              ><span></div>`
          )}
          ${when(
            this.canShowCommitterEmailDropdown(),
            () => html`<div class="rebaseWithCommitterEmail"
            >Rebase with committer email
                <gr-dropdown-list
                    .items=${this.getCommitterEmailDropdownItems()}
                    .value=${this.selectedEmailForRebase}
                    @value-change=${this.handleCommitterEmailDropdownItems}
                >
                </gr-dropdown-list>
                <span></div>`
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

  isCurrentUserEqualToLatestUploader() {
    if (!this.account || !this.uploader) return true;
    return this.account._account_id === this.uploader._account_id;
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

  private setPreferredAsSelectedEmailForRebase(emails: EmailInfo[]) {
    emails.forEach(e => {
      if (e.preferred) {
        this.selectedEmailForRebase = e.email;
      }
    });
  }

  private canShowCommitterEmailDropdown() {
    return (
      this.committerEmailDropdownItems &&
      this.committerEmailDropdownItems.length > 1 &&
      !this.shouldRebaseChain
    );
  }

  private getCommitterEmailDropdownItems() {
    return this.committerEmailDropdownItems?.map(e => {
      return {
        text: e.email,
        value: e.email,
      };
    });
  }

  private isLatestCommitterEmailInDropdownItems(): boolean {
    return this.committerEmailDropdownItems?.some(
      e => e.email === this.latestCommitter?.email.toString()
    );
  }

  public setSelectedEmailForRebase() {
    if (this.isLatestCommitterEmailInDropdownItems()) {
      this.selectedEmailForRebase = this.latestCommitter?.email;
    } else {
      this.setPreferredAsSelectedEmailForRebase(
        this.committerEmailDropdownItems
      );
    }
  }

  async loadCommitterEmailDropdownItems() {
    if (this.isCurrentUserEqualToLatestUploader() || this.allowConflicts) {
      const currentUserEmails = await this.restApiService.getAccountEmails();
      this.committerEmailDropdownItems = currentUserEmails || [];
    } else if (this.uploader && this.uploader.email) {
      const currentUploaderEmails =
        await this.restApiService.getAccountEmailsFor(
          this.uploader.email.toString(),
          () => {}
        );
      this.committerEmailDropdownItems = currentUploaderEmails || [];
    } else {
      this.committerEmailDropdownItems = [];
    }
    if (this.committerEmailDropdownItems) {
      this.setSelectedEmailForRebase();
    }
  }

  private handleCommitterEmailDropdownItems(e: CustomEvent<{value: string}>) {
    this.selectedEmailForRebase = e.detail.value;
  }

  filterChanges(
    input: string,
    changes: RebaseChange[]
  ): AutocompleteSuggestion[] {
    return changes
      .filter(
        change => change.name.includes(input) && change.value !== this.changeNum
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
    if (this.rebaseOnParentInput?.checked) {
      return null;
    }
    if (this.rebaseOnTipInput?.checked) {
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
      allowConflicts: !!this.rebaseAllowConflicts?.checked,
      rebaseChain: !!this.rebaseChain?.checked,
      onBehalfOfUploader: this.rebaseOnBehalfOfUploader(),
      committerEmail: this.rebaseChain?.checked
        ? null
        : this.selectedEmailForRebase || null,
    };
    fireNoBubbleNoCompose(this, 'confirm-rebase', detail);
    this.text = '';
  }

  private rebaseOnBehalfOfUploader() {
    if (this.allowConflicts) return false;
    return true;
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubbleNoCompose(this, 'cancel', {});
    this.text = '';
  }

  private handleRebaseOnOther() {
    this.parentInput.focus();
  }

  private handleEnterChangeNumberClick() {
    if (this.rebaseOnOtherInput) this.rebaseOnOtherInput.checked = true;
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
      if (this.rebaseOnParentInput) this.rebaseOnParentInput.checked = true;
    } else if (this.displayTipOption()) {
      if (this.rebaseOnTipInput) this.rebaseOnTipInput.checked = true;
    } else {
      if (this.rebaseOnOtherInput) this.rebaseOnOtherInput.checked = true;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-rebase-dialog': GrConfirmRebaseDialog;
  }
  interface HTMLElementEventMap {
    'confirm-rebase': CustomEvent<ConfirmRebaseEventDetail>;
  }
}
