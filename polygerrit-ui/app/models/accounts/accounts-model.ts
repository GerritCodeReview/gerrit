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
  private inFlight = new Map<
    UserId,
    Promise<AccountDetailInfo | AccountInfo>
  >();

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

  getAccount(
    partialAccount: AccountInfo
  ): Promise<AccountDetailInfo | AccountInfo> {
    const current = this.getState();
    const id = getUserId(partialAccount);
    if (hasOwnProperty(current.accounts, id)) {
      return Promise.resolve({...current.accounts[id]});
    }
    if (this.inFlight.has(id)) {
      return this.inFlight.get(id)!;
    }

    const promise = this.restApiService
      .getAccountDetails(id, () => {
        this.updateStateAccount(id, partialAccount);
      })
      .then(account => {
        if (account) this.updateStateAccount(id, account);
        return account ?? partialAccount;
      })
      .finally(() => {
        this.inFlight.delete(id);
      });

    this.inFlight.set(id, promise);
    return promise;
  }

  fillDetails(account: AccountInfo): Promise<AccountDetailInfo | AccountInfo> {
    if (!isDetailedAccount(account)) {
      return this.getAccount(account);
    }
    return Promise.resolve(account);
  }
}
