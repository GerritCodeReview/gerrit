/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {from, Subscription} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {routerChangeNum$} from '../router/router-model';
import {
  change$,
  updateStateChange,
  updateStatePath,
  currentPatchNum$,
  changeNum$,
  updateStateReviewedFiles,
  updateStateFileReviewed,
} from './change-model';
import {ParsedChangeInfo} from '../../types/types';
import {ChangeInfo, PatchSetNum, NumericChangeId} from '../../types/common';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
} from '../../utils/patch-set-util';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {Finalizable} from '../registry';
import {combineLatest} from 'rxjs';
import {fireAlert} from '../../utils/event-util';
import { UserModel } from '../user/user-model';

const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';

export class ChangeService implements Finalizable {
  private change?: ParsedChangeInfo;

  private readonly subscriptions: Subscription[] = [];

  private loggedIn?: boolean;

  constructor(readonly restApiService: RestApiService, readonly userModel: UserModel) {
    // TODO: In the future we will want to make restApiService.getChangeDetail()
    // calls from a switchMap() here. For now just make sure to invalidate the
    // change when no changeNum is set.
    this.subscriptions.push(
      routerChangeNum$
        .pipe(
          // The change service is currently a singleton, so we have to be
          // careful to avoid situations where the application state is
          // partially set for the old change where the user is coming from,
          // and partially for the new change where the user is navigating to.
          // So setting the change explicitly to undefined when the user
          // moves away from diff and change pages (changeNum === undefined)
          // helps with that.
          switchMap(changeNum =>
            from(this.restApiService.getChangeDetail(changeNum))
          )
        )
        .subscribe(change => {
          updateStateChange(change ?? undefined);
        })
    );
    this.subscriptions.push(
      this.userModel.account$.subscribe(account => {
        this.loggedIn = !!account;
      })
    );
    this.subscriptions.push(
      change$.subscribe(change => {
        this.change = change;
      })
    );
    this.subscriptions.push(
      combineLatest([currentPatchNum$, changeNum$]).subscribe(
        ([currentPatchNum, changeNum]) => {
          if (!changeNum || !currentPatchNum) {
            updateStateReviewedFiles(new Set([]));
            return;
          }
          this.fetchReviewedFiles(currentPatchNum!, changeNum!);
        }
      )
    );
  }

  fetchReviewedFiles(currentPatchNum: PatchSetNum, changeNum: NumericChangeId) {
    if (!this.loggedIn) return;
    this.restApiService
      .getReviewedFiles(changeNum, currentPatchNum)
      .then(files => {
        updateStateReviewedFiles(new Set(files ?? []));
      });
  }

  setReviewedFilesStatus(
    changeNum: NumericChangeId,
    patchNum: PatchSetNum,
    file: string,
    reviewed: boolean
  ) {
    return this.restApiService
      .saveFileReviewed(changeNum, patchNum, file, reviewed)
      .then(() => {
        updateStateFileReviewed(file, reviewed);
      })
      .catch(() => {
        fireAlert(document, ERR_REVIEW_STATUS);
      });
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions.splice(0, this.subscriptions.length);
  }

  // Temporary workaround until path is derived in the model itself.
  updatePath(path?: string) {
    updateStatePath(path);
  }

  /**
   * Typically you would just subscribe to change$ yourself to get updates. But
   * sometimes it is nice to also be able to get the current ChangeInfo on
   * demand. So here it is for your convenience.
   */
  getChange() {
    return this.change;
  }

  /**
   * Check whether there is no newer patch than the latest patch that was
   * available when this change was loaded.
   *
   * @return A promise that yields true if the latest patch
   *     has been loaded, and false if a newer patch has been uploaded in the
   *     meantime. The promise is rejected on network error.
   */
  fetchChangeUpdates(change: ChangeInfo | ParsedChangeInfo) {
    const knownLatest = computeLatestPatchNum(computeAllPatchSets(change));
    return this.restApiService.getChangeDetail(change._number).then(detail => {
      if (!detail) {
        const error = new Error('Change detail not found.');
        return Promise.reject(error);
      }
      const actualLatest = computeLatestPatchNum(computeAllPatchSets(detail));
      if (!actualLatest || !knownLatest) {
        const error = new Error('Unable to check for latest patchset.');
        return Promise.reject(error);
      }
      return {
        isLatest: actualLatest <= knownLatest,
        newStatus: change.status !== detail.status ? detail.status : null,
        newMessages:
          (change.messages || []).length < (detail.messages || []).length
            ? detail.messages![detail.messages!.length - 1]
            : undefined,
      };
    });
  }
}
