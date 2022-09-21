/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo, AccountInfo} from '../../api/rest-api';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../services/registry';
import {UserId} from '../../types/common';
import {getUserId, isDetailedAccount} from '../../utils/account-util';
import {define} from '../dependency';
import {Model} from '../model';

export interface AccountsState {
  accounts: {[id: UserId]: AccountDetailInfo};
}

export const accountsModelToken = define<AccountsModel>('accounts-model');

export class AccountsModel extends Model<AccountsState> implements Finalizable {
  constructor(readonly restApiService: RestApiService) {
    super({
      accounts: {},
    });
  }

  private updateStateAccount(id: UserId, account?: AccountDetailInfo) {
    const current = {...this.subject$.getValue()};
    if (!account) return;
    current.accounts = {...current.accounts, [id]: account};
    this.setState(current);
  }

  async getAccount(partialAccount: AccountInfo) {
    const current = this.subject$.getValue();
    const id = getUserId(partialAccount);
    if (current.accounts[id]) return current.accounts[id];
    // It is possible to add emails to CC when they don't have a Gerrit
    // account. In this case getAccountDetails will return a 404 error hence
    // pass an empty error function to handle that.
    const account = await this.restApiService.getAccountDetails(id, () => {
      this.updateStateAccount(id, partialAccount as AccountDetailInfo);
      return;
    });
    if (account) this.updateStateAccount(id, account);
    return account;
  }

  async fillDetails(account: AccountInfo) {
    if (!isDetailedAccount(account)) {
      if (account.email) return await this.getAccount({email: account.email});
    }
    return account;
  }
}
