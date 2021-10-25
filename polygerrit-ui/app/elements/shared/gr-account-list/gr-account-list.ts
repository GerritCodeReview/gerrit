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
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-list_html';
import {appContext} from '../../../services/app-context';
import {customElement, property} from '@polymer/decorators';
import {
  ChangeInfo,
  Suggestion,
  AccountInfo,
  GroupInfo,
  EmailAddress,
} from '../../../types/common';
import {
  ReviewerSuggestionsProvider,
  SuggestionItem,
} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {GrAccountEntry} from '../gr-account-entry/gr-account-entry';
import {GrAccountChip} from '../gr-account-chip/gr-account-chip';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {PaperInputElementExt} from '../../../types/types';
import {fireAlert} from '../../../utils/event-util';
import {accountOrGroupKey} from '../../../utils/account-util';

const VALID_EMAIL_ALERT = 'Please input a valid email.';

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-list': GrAccountList;
  }
}

export interface GrAccountList {
  $: {
    entry: GrAccountEntry;
  };
}

/**
 * For item added with account info
 */
export interface AccountObjectInput {
  account: AccountInfo;
}

/**
 * For item added with group info
 */
export interface GroupObjectInput {
  group: GroupInfo;
  confirm: boolean;
}

/** Supported input to be added */
export type RawAccountInput = string | AccountObjectInput | GroupObjectInput;

// type guards for AccountObjectInput and GroupObjectInput
function isAccountObject(x: RawAccountInput): x is AccountObjectInput {
  return !!(x as AccountObjectInput).account;
}

function isGroupObjectInput(x: RawAccountInput): x is GroupObjectInput {
  return !!(x as GroupObjectInput).group;
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

type AccountInput = AccountInfoInput | GroupInfoInput;

export interface AccountAddition {
  account?: AccountInfoInput;
  group?: GroupInfoInput;
}

@customElement('gr-account-list')
export class GrAccountList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when user inputs an invalid email address.
   *
   * @event show-alert
   */

  @property({type: Array, notify: true})
  accounts: AccountInput[] = [];

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  filter?: (input: Suggestion) => boolean;

  @property({type: String})
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
  @property({type: Object, notify: true})
  pendingConfirmation: GroupObjectInput | null = null;

  @property({type: Boolean})
  readonly = false;

  /**
   * When true, allows for non-suggested inputs to be added.
   */
  @property({type: Boolean})
  allowAnyInput = false;

  /**
   * Array of values (groups/accounts) that are removable. When this prop is
   * undefined, all values are removable.
   */
  @property({type: Array})
  removableValues?: AccountInput[];

  @property({type: Number})
  maxCount = 0;

  /**
   * Returns suggestion items
   */
  @property({type: Object})
  _querySuggestions: (input: string) => Promise<SuggestionItem[]>;

  reporting: ReportingService;

  private pendingRemoval: Set<AccountInput> = new Set();

  constructor() {
    super();
    this.reporting = appContext.reportingService;
    this._querySuggestions = input => this._getSuggestions(input);
    this.addEventListener('remove', e =>
      this._handleRemove(e as CustomEvent<{account: AccountInput}>)
    );
  }

  get accountChips() {
    return Array.from(this.root?.querySelectorAll('gr-account-chip') || []);
  }

  get focusStart() {
    return this.$.entry.focusStart;
  }

  _getSuggestions(input: string) {
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

  _handleAdd(e: CustomEvent<{value: RawAccountInput}>) {
    this.addAccountItem(e.detail.value);
  }

  addAccountItem(item: RawAccountInput) {
    // Append new account or group to the accounts property. We add our own
    // internal properties to the account/group here, so we clone the object
    // to avoid cluttering up the shared change object.
    let itemTypeAdded = 'unknown';
    if (isAccountObject(item)) {
      const account = {...item.account, _pendingAdd: true};
      this.removeFromPendingRemoval(account);
      this.push('accounts', account);
      itemTypeAdded = 'account';
    } else if (isGroupObjectInput(item)) {
      if (item.confirm) {
        this.pendingConfirmation = item;
        return;
      }
      const group = {...item.group, _pendingAdd: true, _group: true};
      this.push('accounts', group);
      this.removeFromPendingRemoval(group);
      itemTypeAdded = 'group';
    } else if (this.allowAnyInput) {
      if (!item.includes('@')) {
        // Repopulate the input with what the user tried to enter and have
        // a toast tell them why they can't enter it.
        this.$.entry.setText(item);
        fireAlert(this, VALID_EMAIL_ALERT);
        return false;
      } else {
        const account = {email: item as EmailAddress, _pendingAdd: true};
        this.push('accounts', account);
        this.removeFromPendingRemoval(account);
        itemTypeAdded = 'email';
      }
    }

    this.reporting.reportInteraction(`Add to ${this.id}`, {itemTypeAdded});
    this.pendingConfirmation = null;
    return true;
  }

  confirmGroup(group: GroupInfo) {
    this.push('accounts', {
      ...group,
      confirmed: true,
      _pendingAdd: true,
      _group: true,
    });
    this.pendingConfirmation = null;
  }

  _computeChipClass(account: AccountInput) {
    const classes = [];
    if (account._group) {
      classes.push('group');
    }
    if (account._pendingAdd) {
      classes.push('pendingAdd');
    }
    return classes.join(' ');
  }

  _computeRemovable(account: AccountInput, readonly: boolean) {
    if (readonly) {
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

  _handleRemove(e: CustomEvent<{account: AccountInput}>) {
    const toRemove = e.detail.account;
    this.removeAccount(toRemove);
    this.$.entry.focus();
  }

  removeAccount(toRemove?: AccountInput) {
    if (!toRemove || !this._computeRemovable(toRemove, this.readonly)) {
      return;
    }
    for (let i = 0; i < this.accounts.length; i++) {
      if (accountOrGroupKey(toRemove) === accountOrGroupKey(this.accounts[i])) {
        this.splice('accounts', i, 1);
        this.pendingRemoval.add(toRemove);
        this.reporting.reportInteraction(`Remove from ${this.id}`);
        return;
      }
    }
    this.reporting.error(
      new Error(`Received "remove" event for missing account: ${toRemove}`)
    );
  }

  _getNativeInput(paperInput: PaperInputElementExt) {
    // In Polymer 2 inputElement isn't nativeInput anymore
    return (paperInput.$.nativeInput ||
      paperInput.inputElement) as HTMLTextAreaElement;
  }

  _handleInputKeydown(
    e: CustomEvent<{input: PaperInputElementExt; keyCode: number}>
  ) {
    const input = this._getNativeInput(e.detail.input);
    if (
      input.selectionStart !== input.selectionEnd ||
      input.selectionStart !== 0
    ) {
      return;
    }
    switch (e.detail.keyCode) {
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

  _handleChipKeydown(e: KeyboardEvent) {
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
          this.$.entry.focus();
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
          this.$.entry.focus();
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
    const text = this.$.entry.getText();
    if (!text.length) {
      return true;
    }
    const wasSubmitted = this.addAccountItem(text);
    if (wasSubmitted) {
      this.$.entry.clear();
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

  removeFromPendingRemoval(account: AccountInput) {
    this.pendingRemoval.delete(account);
  }

  clearPendingRemovals() {
    this.pendingRemoval.clear();
  }

  _computeEntryHidden(
    maxCount: number,
    accountsRecord: PolymerDeepPropertyChange<AccountInput[], AccountInput[]>,
    readonly: boolean
  ) {
    return (maxCount && maxCount <= accountsRecord.base.length) || readonly;
  }
}
