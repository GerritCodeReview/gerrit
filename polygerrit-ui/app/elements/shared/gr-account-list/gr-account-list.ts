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
  SuggestedReviewerAccountInfo,
  SuggestedReviewerInfo,
} from '../../../types/common';
import {ReviewerSuggestionsProvider} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {GrAccountEntry} from '../gr-account-entry/gr-account-entry';
import {GrAccountChip} from '../gr-account-chip/gr-account-chip';
import {PaperInputElementExt} from '../../../types/types';
import {fire, fireAlert} from '../../../utils/event-util';
import {accountOrGroupKey} from '../../../utils/account-util';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {classMap} from 'lit/directives/class-map';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {ValueChangedEvent} from '../../../types/events';
import {queryAndAssert} from '../../../utils/common-util';

const VALID_EMAIL_ALERT = 'Please input a valid email.';

declare global {
  interface HTMLElementEventMap {
    'accounts-changed': ValueChangedEvent<(AccountInfo | GroupInfo)[]>;
    'pending-confirmation-changed': ValueChangedEvent<SuggestedReviewerGroupInfo | null>;
  }
  interface HTMLElementTagNameMap {
    'gr-account-list': GrAccountList;
  }
  interface HTMLElementEventMap {
    'account-added': CustomEvent<AccountInputDetail>;
  }
}
export interface AccountInputDetail {
  account: AccountInput;
}

/** Supported input to be added */
export type RawAccountInput =
  | string
  | SuggestedReviewerAccountInfo
  | SuggestedReviewerGroupInfo;

// type guards for SuggestedReviewerAccountInfo and SuggestedReviewerGroupInfo
function isAccountObject(
  x: RawAccountInput
): x is SuggestedReviewerAccountInfo {
  return !!(x as SuggestedReviewerAccountInfo).account;
}

function isSuggestedReviewerGroupInfo(
  x: RawAccountInput
): x is SuggestedReviewerGroupInfo {
  return !!(x as SuggestedReviewerGroupInfo).group;
}

// Internal input type with account info
export interface AccountInfoInput extends AccountInfo {
  _group?: boolean;
  _account?: boolean;
  _pendingAdd?: boolean;
  confirmed?: boolean;
}

// Internal input type with group info
export interface GroupInfoInput extends GroupInfo {
  _group?: boolean;
  _account?: boolean;
  _pendingAdd?: boolean;
  confirmed?: boolean;
}

function isAccountInfoInput(x: AccountInput): x is AccountInfoInput {
  const input = x as AccountInfoInput;
  return !!input._account || !!input._account_id || !!input.email;
}

function isGroupInfoInput(x: AccountInput): x is GroupInfoInput {
  const input = x as GroupInfoInput;
  return !!input._group || !!input.id;
}

export type AccountInput = AccountInfoInput | GroupInfoInput;

export interface AccountAddition {
  account?: AccountInfoInput;
  group?: GroupInfoInput;
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

  private pendingRemoval: Set<AccountInput> = new Set();

  constructor() {
    super();
    this.querySuggestions = input => this.getSuggestions(input);
    this.addEventListener('remove', e =>
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
      .pending-add {
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
        ${this.accounts.map(
          account => html`
            <gr-account-chip
              .account=${account}
              class=${classMap({
                group: !!account._group,
                pendingAdd: !!account._pendingAdd,
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

  addAccountItem(item: RawAccountInput) {
    // Append new account or group to the accounts property. We add our own
    // internal properties to the account/group here, so we clone the object
    // to avoid cluttering up the shared change object.
    let account;
    let group;
    let itemTypeAdded = 'unknown';
    if (isAccountObject(item)) {
      account = {...item.account, _pendingAdd: true};
      this.removeFromPendingRemoval(account);
      this.accounts.push(account);
      itemTypeAdded = 'account';
    } else if (isSuggestedReviewerGroupInfo(item)) {
      if (item.confirm) {
        this.pendingConfirmation = item;
        return;
      }
      group = {...item.group, _pendingAdd: true, _group: true};
      this.accounts.push(group);
      this.removeFromPendingRemoval(group);
      itemTypeAdded = 'group';
    } else if (this.allowAnyInput) {
      if (!item.includes('@')) {
        // Repopulate the input with what the user tried to enter and have
        // a toast tell them why they can't enter it.
        this.entry?.setText(item);
        fireAlert(this, VALID_EMAIL_ALERT);
        return false;
      } else {
        account = {email: item as EmailAddress, _pendingAdd: true};
        this.accounts.push(account);
        this.removeFromPendingRemoval(account);
        itemTypeAdded = 'email';
      }
    }
    fire(this, 'accounts-changed', {value: this.accounts.slice()});
    fire(this, 'account-added', {account: (account ?? group)! as AccountInput});
    this.reporting.reportInteraction(`Add to ${this.id}`, {itemTypeAdded});
    this.pendingConfirmation = null;
    this.requestUpdate();
    return true;
  }

  confirmGroup(group: GroupInfo) {
    this.accounts.push({
      ...group,
      confirmed: true,
      _pendingAdd: true,
      _group: true,
    });
    this.pendingConfirmation = null;
    fire(this, 'accounts-changed', {value: this.accounts});
    this.requestUpdate();
  }

  // private but used in test
  computeChipClass(account: AccountInput) {
    const classes = [];
    if (account._group) {
      classes.push('group');
    }
    if (account._pendingAdd) {
      classes.push('pendingAdd');
    }
    return classes.join(' ');
  }

  // private but used in test
  computeRemovable(account: AccountInput) {
    if (this.readonly) {
      return false;
    }
    if (this.removableValues) {
      for (let i = 0; i < this.removableValues.length; i++) {
        if (
          accountOrGroupKey(this.removableValues[i]) ===
          accountOrGroupKey(account)
        ) {
          return true;
        }
      }
      return !!account._pendingAdd;
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
      return;
    }
    for (let i = 0; i < this.accounts.length; i++) {
      if (accountOrGroupKey(toRemove) === accountOrGroupKey(this.accounts[i])) {
        this.accounts.splice(i, 1);
        this.pendingRemoval.add(toRemove);
        this.reporting.reportInteraction(`Remove from ${this.id}`);
        this.requestUpdate();
        fire(this, 'accounts-changed', {value: this.accounts.slice()});
        return;
      }
    }
    this.reporting.error(
      new Error(`Received "remove" event for missing account: ${toRemove}`)
    );
  }

  // private but used in test
  getOwnNativeInput(paperInput: PaperInputElementExt) {
    // In Polymer 2 inputElement isn't nativeInput anymore
    return (paperInput.$.nativeInput ||
      paperInput.inputElement) as HTMLTextAreaElement;
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
    switch (e.keyCode) {
      case 8: // Backspace
        this.removeAccount(this.accounts[this.accounts.length - 1]);
        break;
      case 37: // Left arrow
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
    switch (e.keyCode) {
      case 8: // Backspace
      case 13: // Enter
      case 32: // Spacebar
      case 46: // Delete
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
      case 37: // Left arrow
        if (index > 0) {
          chip.blur();
          chips[index - 1].focus();
        }
        break;
      case 39: // Right arrow
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

  additions(): AccountAddition[] {
    return this.accounts
      .filter(account => account._pendingAdd)
      .map(account => {
        if (isGroupInfoInput(account)) {
          return {group: account};
        } else if (isAccountInfoInput(account)) {
          return {account};
        } else {
          throw new Error('AccountInput must be either Account or Group.');
        }
      });
  }

  removals(): AccountAddition[] {
    return Array.from(this.pendingRemoval).map(account => {
      if (isGroupInfoInput(account)) {
        return {group: account};
      } else if (isAccountInfoInput(account)) {
        return {account};
      } else {
        throw new Error('AccountInput must be either Account or Group.');
      }
    });
  }

  private removeFromPendingRemoval(account: AccountInput) {
    this.pendingRemoval.delete(account);
  }

  clearPendingRemovals() {
    this.pendingRemoval.clear();
  }
}
