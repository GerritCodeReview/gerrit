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
import {
  combineLatest,
  from,
  fromEvent,
  Observable,
  Subscription,
  of,
} from 'rxjs';
import {map, startWith, switchMap} from 'rxjs/operators';
import {routerChangeNum$} from '../router/router-model';
import {
  change$,
  updateStateChange,
  updateStateLoading,
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
import {fireAlert} from '../../utils/event-util';

const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';

export class ChangeService implements Finalizable {
  private change?: ParsedChangeInfo;

  private readonly subscriptions: Subscription[] = [];

  private currentPatchNum?: PatchSetNum;

  // For usage in `combineLatest` we need `startWith` such that reload$ has an
  // initial value.
  private readonly reload$: Observable<unknown> = fromEvent(
    document,
    'reload'
  ).pipe(startWith(undefined));

  constructor(readonly restApiService: RestApiService) {
    this.subscriptions.push(
      combineLatest([routerChangeNum$, this.reload$])
        .pipe(
          map(([changeNum, _]) => changeNum),
          switchMap(changeNum => {
            if (changeNum !== undefined) updateStateLoading(changeNum);
            return from(this.restApiService.getChangeDetail(changeNum));
          })
        )
        .subscribe(change => {
          // The change service is currently a singleton, so we have to be
          // careful to avoid situations where the application state is
          // partially set for the old change where the user is coming from,
          // and partially for the new change where the user is navigating to.
          // So setting the change explicitly to undefined when the user
          // moves away from diff and change pages (changeNum === undefined)
          // helps with that.
          updateStateChange(change ?? undefined);
        })
    );
    this.subscriptions.push(
      change$.subscribe(change => {
        this.change = change;
      })
    );
    this.subscriptions.push(
      currentPatchNum$.subscribe(
        currentPatchNum => (this.currentPatchNum = currentPatchNum)
      )
    );
    combineLatest([currentPatchNum$, changeNum$]).pipe(
      switchMap(([currentPatchNum, changeNum]) => {
        if (!changeNum || !currentPatchNum) {
          updateStateReviewedFiles([]);
          return of(undefined);
        }
        return from(this.fetchReviewedFiles(currentPatchNum!, changeNum!));
      })
    );
  }

  fetchReviewedFiles(currentPatchNum: PatchSetNum, changeNum: NumericChangeId) {
    return this.restApiService.getLoggedIn().then(loggedIn => {
      if (!loggedIn) return;
      this.restApiService
        .getReviewedFiles(changeNum, currentPatchNum)
        .then(files => {
          if (
            changeNum !== this.change?._number ||
            currentPatchNum !== this.currentPatchNum
          )
            return;
          updateStateReviewedFiles(files ?? []);
        });
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
