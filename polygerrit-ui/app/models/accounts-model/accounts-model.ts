/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo, AccountId, EmailAddress} from '../../api/rest-api';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../services/registry';
import {UserId} from '../../types/common';
import {Model} from '../model';

export interface AccountsState {
  accounts: {[id: UserId]: AccountDetailInfo};
}

export class AccountsModel extends Model<AccountsState> implements Finalizable {
  constructor(readonly restApiService: RestApiService) {
    super({
      accounts: {},
    });
  }

  finalize() {}

  private updateStateAccount(
    id: AccountId | EmailAddress,
    account?: AccountDetailInfo
  ) {
    const current = {...this.subject$.getValue()};
    if (account) {
      current.accounts = {...current.accounts, [id]: account};
    }
    this.subject$.next(current);
  }

  async getAccount(id: AccountId | EmailAddress) {
    const current = this.subject$.getValue();
    if (current.accounts[id]) return current.accounts[id];
    const account = await this.restApiService.getAccountDetails(id);
    this.updateStateAccount(id, account);
    return account;
  }
}
