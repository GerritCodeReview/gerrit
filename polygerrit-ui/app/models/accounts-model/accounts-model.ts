/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {AccountDetailInfo, AccountInfo} from '../../api/rest-api';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {UserId} from '../../types/common';
import {getUserId, isDetailedAccount} from '../../utils/account-util';
import {hasOwnProperty} from '../../utils/common-util';
import {define} from '../dependency';
import {Model} from '../model';

// To avoid sync issues and running multiple API requests, we
// cache promises in state.
export interface AccountsState {
  accounts: {
    [id: UserId]: Promise<AccountDetailInfo | AccountInfo | undefined>;
  };
}

export const accountsModelToken = define<AccountsModel>('accounts-model');

export class AccountsModel extends Model<AccountsState> {
  constructor(readonly restApiService: RestApiService) {
    super({
      accounts: {},
    });
  }

  private updateStateAccount(
    id: UserId,
    account: Promise<AccountDetailInfo | AccountInfo | undefined>
  ) {
    if (!account) return;
    const current = {...this.getState()};
    current.accounts = {...current.accounts, [id]: account};
    this.setState(current);
  }

  async getAccount(partialAccount: AccountInfo) {
    const current = this.getState();
    const id = getUserId(partialAccount);
    if (hasOwnProperty(current.accounts, id)) return current.accounts[id];
    // It is possible to add emails to CC when they don't have a Gerrit
    // account. In this case getAccountDetails will return a 404 error hence
    // pass an empty error function to handle that.
    const accountPromise = this.restApiService.getAccountDetails(id, () => {
      this.updateStateAccount(id, Promise.resolve(partialAccount));
      return;
    });
    this.updateStateAccount(id, accountPromise);
    return accountPromise;
  }

  async fillDetails(account: AccountInfo) {
    if (!isDetailedAccount(account)) {
      if (account.email) return await this.getAccount({email: account.email});
      else if (account._account_id)
        return await this.getAccount({_account_id: account._account_id});
    }
    return account;
  }
}
