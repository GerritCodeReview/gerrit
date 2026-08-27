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
  private inFlight = new Map<UserId, Promise<AccountDetailInfo | AccountInfo>>();

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
    this.updateState({
      accounts: {...this.getState().accounts, [id]: account},
    });
  }

  async getAccount(
    partialAccount: AccountInfo
  ): Promise<AccountDetailInfo | AccountInfo> {
    const current = this.getState();
    const id = getUserId(partialAccount);
    if (hasOwnProperty(current.accounts, id)) return {...current.accounts[id]};
    if (this.inFlight.has(id)) {
      const result = await this.inFlight.get(id)!;
      return {...result};
    }

    const promise = (async () => {
      // It is possible to add emails to CC when they don't have a Gerrit
      // account. In this case getAccountDetails will return a 404 error then
      // we at least use what is in partialAccount.
      const account = await this.restApiService.getAccountDetails(id, () => {
        this.updateStateAccount(id, partialAccount);
        return;
      });
      if (account) this.updateStateAccount(id, account);
      return account ?? partialAccount;
    })();

    this.inFlight.set(id, promise);
    try {
      const result = await promise;
      return {...result};
    } finally {
      this.inFlight.delete(id);
    }
  }

  async fillDetails(account: AccountInfo) {
    if (!isDetailedAccount(account)) {
      return await this.getAccount(account);
    }
    return account;
  }
}
