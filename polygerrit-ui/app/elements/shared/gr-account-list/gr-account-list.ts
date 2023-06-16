/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-account-chip/gr-account-chip';
import '../gr-account-entry/gr-account-entry';
import {getAppContext} from '../../../services/app-context';
import {
  ChangeInfo,
  Suggestion,
  AccountInfo,
  GroupInfo,
  EmailAddress,
  SuggestedReviewerGroupInfo,
  SuggestedReviewerInfo,
  isGroup,
} from '../../../types/common';
import {ReviewerSuggestionsProvider} from '../../../services/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {GrAccountEntry} from '../gr-account-entry/gr-account-entry';
import {GrAccountChip} from '../gr-account-chip/gr-account-chip';
import {fire, fireAlert} from '../../../utils/event-util';
import {
  AccountInput,
  getUserId,
  isAccountNewlyAdded,
  isAccountObject,
  isSuggestedReviewerGroupInfo,
  RawAccountInput,
} from '../../../utils/account-util';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {classMap} from 'lit/directives/class-map.js';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {ValueChangedEvent} from '../../../types/events';
import {difference, queryAndAssert} from '../../../utils/common-util';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {IronInputElement} from '@polymer/iron-input';
import {ReviewerState} from '../../../api/rest-api';
import {repeat} from 'lit/directives/repeat.js';

const VALID_EMAIL_ALERT = 'Please input a valid email.';
const VALID_USER_GROUP_ALERT = 'Please input a valid user or group.';

declare global {
  interface HTMLElementEventMap {
    'accounts-changed': ValueChangedEvent<(AccountInfo | GroupInfo)[]>;
    'pending-confirmation-changed': ValueChangedEvent<SuggestedReviewerGroupInfo | null>;
    'account-added': CustomEvent<{account: AccountInfo | GroupInfo}>;
  }
  interface HTMLElementTagNameMap {
    'gr-account-list': GrAccountList;
  }
}

@customElement('gr-account-list')
export class GrAccountList extends LitElement {
  /**
   * Fired when user inputs an invalid email address.
   *
   * @event show-alert
   */
  @query('#entry') entry?: GrAccountEntry;

  @property({type: Array})
  accounts: AccountInput[] = [];

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  filter?: (input: Suggestion) => boolean;

  @property()
  placeholder = '';

  @property({type: Boolean})
  disabled = false;

  @property({type: String})
  reviewerState?: ReviewerState;

  /**
   * Returns suggestions and convert them to list item
   */
  @property({type: Object})
  suggestionsProvider?: ReviewerSuggestionsProvider;

  /**
   * Needed for template checking since value is initially set to null.
   */
  @property({type: Object})
  pendingConfirmation: SuggestedReviewerGroupInfo | null = null;

  @property({type: Boolean})
  readonly = false;

  /**
   * When true, allows for non-suggested inputs to be added.
   */
  @property({type: Boolean, attribute: 'allow-any-input'})
  allowAnyInput = false;

  /**
   * Array of values (groups/accounts) that are removable. When this prop is
   * undefined, all values are removable.
   */
  @property({type: Array})
  removableValues?: AccountInput[];

  /**
   * Returns suggestion items
   */
  @state() private querySuggestions: AutocompleteQuery<SuggestedReviewerInfo>;

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    this.querySuggestions = input => this.getSuggestions(input);
    this.addEventListener('remove-account', e =>
      this.handleRemove(e as CustomEvent<{account: AccountInput}>)
    );
  }

  static override styles = [
    sharedStyles,
    css`
      gr-account-chip {
        display: inline-block;
        margin: var(--spacing-xs) var(--spacing-xs) var(--spacing-xs) 0;
      }
      gr-account-entry {
        display: flex;
        flex: 1;
        min-width: 10em;
        margin: var(--spacing-xs) var(--spacing-xs) var(--spacing-xs) 0;
      }
      .group {
        --account-label-suffix: ' (group)';
      }
      .newlyAdded {
        font-style: italic;
      }
      .list {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
      }
    `,
  ];

  override render() {
    return html`<div class="list">
        ${repeat(
          this.accounts,
          account => getUserId(account),
          account => html`
            <gr-account-chip
              .account=${account}
              .change=${this.change}
              class=${classMap({
                group: isGroup(account),
                newlyAdded: isAccountNewlyAdded(
                  account,
                  this.reviewerState,
                  this.change
                ),
              })}
              ?removable=${this.computeRemovable(account)}
              @keydown=${this.handleChipKeydown}
              tabindex="-1"
            >
            </gr-account-chip>
          `
        )}
      </div>
      <gr-account-entry
        borderless=""
        ?hidden=${this.readonly}
        id="entry"
        .placeholder=${this.placeholder}
        @add=${this.handleAdd}
        @keydown=${this.handleInputKeydown}
        .allowAnyInput=${this.allowAnyInput}
        .querySuggestions=${this.querySuggestions}
      >
      </gr-account-entry>
      <slot></slot>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('pendingConfirmation')) {
      fire(this, 'pending-confirmation-changed', {
        value: this.pendingConfirmation,
      });
    }
  }

  get accountChips(): GrAccountChip[] {
    return Array.from(
      this.shadowRoot?.querySelectorAll('gr-account-chip') || []
    );
  }

  get focusStart() {
    // Entry is always defined and we cannot return undefined.
    return this.entry?.focusStart;
  }

  getSuggestions(
    input: string
  ): Promise<AutocompleteSuggestion<SuggestedReviewerInfo>[]> {
    const provider = this.suggestionsProvider;
    if (!provider) return Promise.resolve([]);
    return provider.getSuggestions(input).then(suggestions => {
      if (!suggestions) return [];
      if (this.filter) {
        suggestions = suggestions.filter(this.filter);
      }
      return suggestions.map(suggestion =>
        provider.makeSuggestionItem(suggestion)
      );
    });
  }

  // private but used in test
  handleAdd(e: ValueChangedEvent<string>) {
    // TODO(TS) this is temporary hack to avoid cascade of ts issues
    const item = e.detail.value as RawAccountInput;
    this.addAccountItem(item);
  }

  /**
   * Check if account or group is valid and add it.
   *
   * @return true if account or group is added.
   */
  addAccountItem(item: RawAccountInput): boolean {
    // Append new account or group to the accounts property. We add our own
    // internal properties to the account/group here, so we clone the object
    // to avoid cluttering up the shared change object.
    let accountOrGroup: AccountInfo | GroupInfo | undefined;
    let itemTypeAdded = 'unknown';
    if (isAccountObject(item)) {
      accountOrGroup = {...item.account};
      this.accounts.push(accountOrGroup);
      itemTypeAdded = 'account';
    } else if (isSuggestedReviewerGroupInfo(item)) {
      if (item.confirm) {
        this.pendingConfirmation = item;
        return false;
      }
      accountOrGroup = {...item.group};
      this.accounts.push(accountOrGroup);
      itemTypeAdded = 'group';
    } else if (this.allowAnyInput) {
      if (!item.includes('@')) {
        // Repopulate the input with what the user tried to enter and have
        // a toast tell them why they can't enter it.
        this.entry?.setText(item);
        fireAlert(this, VALID_EMAIL_ALERT);
        return false;
      } else {
        accountOrGroup = {email: item as EmailAddress};
        this.accounts.push(accountOrGroup);
        itemTypeAdded = 'email';
      }
    }
    if (!accountOrGroup) {
      fireAlert(this, VALID_USER_GROUP_ALERT);
      return false;
    }
    fire(this, 'accounts-changed', {value: this.accounts.slice()});
    fire(this, 'account-added', {account: accountOrGroup});
    this.reporting.reportInteraction(`Add to ${this.id}`, {itemTypeAdded});
    this.pendingConfirmation = null;
    this.requestUpdate();
    return true;
  }

  confirmGroup(group: GroupInfo) {
    this.accounts.push({
      ...group,
      confirmed: true,
    });
    this.pendingConfirmation = null;
    fire(this, 'accounts-changed', {value: this.accounts});
    this.requestUpdate();
  }

  // private but used in test
  computeRemovable(account: AccountInput) {
    if (this.readonly) {
      return false;
    }
    if (this.removableValues) {
      for (let i = 0; i < this.removableValues.length; i++) {
        if (getUserId(this.removableValues[i]) === getUserId(account)) {
          return true;
        }
      }
      return isAccountNewlyAdded(account, this.reviewerState, this.change);
    }
    return true;
  }

  private handleRemove(e: CustomEvent<{account: AccountInput}>) {
    const toRemove = e.detail.account;
    this.removeAccount(toRemove);
    this.entry?.focus();
  }

  removeAccount(toRemove?: AccountInput) {
    if (!toRemove || !this.computeRemovable(toRemove)) {
      return false;
    }
    for (let i = 0; i < this.accounts.length; i++) {
      if (getUserId(toRemove) === getUserId(this.accounts[i])) {
        this.accounts.splice(i, 1);
        this.reporting.reportInteraction(`Remove from ${this.id}`);
        this.requestUpdate();
        fire(this, 'accounts-changed', {value: this.accounts.slice()});
        return true;
      }
    }
    return false;
  }

  // private but used in test
  getOwnNativeInput(paperInput: PaperInputElement) {
    return (paperInput.inputElement as IronInputElement)
      .inputElement as HTMLInputElement;
  }

  private handleInputKeydown(e: KeyboardEvent) {
    const target = e.target as GrAccountEntry;
    const entryInput = queryAndAssert<GrAutocomplete>(target, '#input');
    const input = this.getOwnNativeInput(entryInput.input!);
    if (
      input.selectionStart !== input.selectionEnd ||
      input.selectionStart !== 0
    ) {
      return;
    }
    switch (e.key) {
      case 'Backspace':
        this.removeAccount(this.accounts[this.accounts.length - 1]);
        break;
      case 'ArrowLeft':
        if (this.accountChips[this.accountChips.length - 1]) {
          this.accountChips[this.accountChips.length - 1].focus();
        }
        break;
    }
  }

  private handleChipKeydown(e: KeyboardEvent) {
    const chip = e.target as GrAccountChip;
    const chips = this.accountChips;
    const index = chips.indexOf(chip);
    switch (e.key) {
      case 'Backspace':
      case 'Enter':
      case ' ':
      case 'Delete':
        this.removeAccount(chip.account);
        // Splice from this array to avoid inconsistent ordering of
        // event handling.
        chips.splice(index, 1);
        if (index < chips.length) {
          chips[index].focus();
        } else if (index > 0) {
          chips[index - 1].focus();
        } else {
          this.entry?.focus();
        }
        break;
      case 'ArrowLeft':
        if (index > 0) {
          chip.blur();
          chips[index - 1].focus();
        }
        break;
      case 'ArrowRight':
        chip.blur();
        if (index < chips.length - 1) {
          chips[index + 1].focus();
        } else {
          this.entry?.focus();
        }
        break;
    }
  }

  /**
   * Submit the text of the entry as a reviewer value, if it exists. If it is
   * a successful submit of the text, clear the entry value.
   *
   * @return If there is text in the entry, return true if the
   * submission was successful and false if not. If there is no text,
   * return true.
   */
  submitEntryText() {
    const text = this.entry?.getText();
    if (!text?.length) {
      return true;
    }
    const wasSubmitted = this.addAccountItem(text);
    if (wasSubmitted) {
      this.entry?.clear();
    }
    return wasSubmitted;
  }

  additions(): AccountInput[] {
    if (!this.change) return [];
    return this.accounts.filter(account =>
      isAccountNewlyAdded(account, this.reviewerState, this.change)
    );
  }

  removals(): AccountInfo[] {
    if (!this.reviewerState) return [];
    return difference(
      this.change?.reviewers[this.reviewerState] ?? [],
      this.accounts,
      (a, b) => getUserId(a) === getUserId(b)
    );
  }
}
