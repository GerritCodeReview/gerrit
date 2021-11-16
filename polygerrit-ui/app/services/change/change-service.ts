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
import {combineLatest} from 'rxjs';
import {fireAlert} from '../../utils/event-util';
import {filter} from 'rxjs/operators';

const ERR_REVIEW_STATUS = 'Couldn’t change file review status.';

export class ChangeService {
  private change?: ParsedChangeInfo;

  constructor(readonly restApiService: RestApiService) {
    // TODO: In the future we will want to make restApiService.getChangeDetail()
    // calls from a switchMap() here. For now just make sure to invalidate the
    // change when no changeNum is set.
    routerChangeNum$.subscribe(changeNum => {
      if (!changeNum) updateStateChange(undefined);
    });
    change$.subscribe(change => {
      this.change = change;
    });
    changeNum$.subscribe(changeNum => {
      if (!changeNum) updateStateReviewedFiles(new Set([]));
    });
    combineLatest([currentPatchNum$, changeNum$])
      .pipe(
        filter(
          ([currentPatchNum, changeNum]) => !!currentPatchNum && !!changeNum
        )
      )
      .subscribe(([currentPatchNum, changeNum]) => {
        this.fetchReviewedFiles(currentPatchNum!, changeNum!);
      });
  }

  fetchReviewedFiles(currentPatchNum: PatchSetNum, changeNum: NumericChangeId) {
    this.restApiService.getLoggedIn().then(loggedIn => {
      if (!loggedIn) return;
      this.restApiService
        .getReviewedFiles(changeNum, currentPatchNum)
        .then(files => {
          updateStateReviewedFiles(new Set(files ?? []));
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

  /**
   * This is a temporary indirection between change-view, which currently
   * manages what the current change is, and the change-model, which will
   * become the source of truth in the future. We will extract a substantial
   * amount of code from change-view and move it into this change-service. This
   * will take some time ...
   */
  updateChange(change: ParsedChangeInfo) {
    updateStateChange(change);
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
