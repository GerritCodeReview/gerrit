/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {Subscription} from 'rxjs';
import {AccountId, AccountInfo, EmailAddress} from '../../api/rest-api';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../services/registry';
import {Model} from '../model';

export interface AccountsState {
  accounts: Map<AccountId | EmailAddress, AccountInfo>;
}

export class AccountsModel extends Model<AccountsState> implements Finalizable {
  private subscriptions: Subscription[] = [];

  constructor(readonly restApiService: RestApiService) {
    super({
      accounts: new Map(),
    });
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }

  async getAccount(id: AccountId | EmailAddress) {
    const current = this.subject$.getValue();
    if (current.accounts.get(id)) return current.accounts.get(id);
    const account = await this.restApiService.getAccount(id);
    if (account) {
      current.accounts.set(id, account);
    }
    this.subject$.next({...current});
    return account;
  }
}
