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
import {Model} from '../base/model';

export interface AccountsState {
  accounts: {
    [id: UserId]: AccountDetailInfo | AccountInfo;
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
    account: AccountDetailInfo | AccountInfo
  ) {
    if (!account) return;
    const current = {...this.getState()};
    current.accounts = {...current.accounts, [id]: account};
    this.setState(current);
  }

  async getAccount(
    partialAccount: AccountInfo
  ): Promise<AccountDetailInfo | AccountInfo> {
    const current = this.getState();
    const id = getUserId(partialAccount);
    if (hasOwnProperty(current.accounts, id)) return {...current.accounts[id]};
    // It is possible to add emails to CC when they don't have a Gerrit
    // account. In this case getAccountDetails will return a 404 error then
    // we at least use what is in partialAccount.
    const account = await this.restApiService.getAccountDetails(id, () => {
      this.updateStateAccount(id, partialAccount);
      return;
    });
    if (account) this.updateStateAccount(id, account);
    return account ?? partialAccount;
  }

  async fillDetails(account: AccountInfo) {
    if (!isDetailedAccount(account)) {
      return await this.getAccount(account);
    }
    return account;
  }
}
