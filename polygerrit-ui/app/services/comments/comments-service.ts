/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {combineLatest, Subscription} from 'rxjs';
import {
  CommentBasics,
  CommentInfo,
  NumericChangeId,
  PatchSetNum,
  RevisionId,
  UrlEncodedCommentId,
} from '../../types/common';
import {
  reportingDetails,
  DraftInfo,
  UnsavedInfo,
} from '../../utils/comment-util';
import {fire, fireAlert, fireEvent} from '../../utils/event-util';
import {CURRENT} from '../../utils/patch-set-util';
import {RestApiService} from '../gr-rest-api/gr-rest-api';
import {
  updateStateSetDraft,
  updateStateDeleteDraft,
  updateStateComments,
  updateStateRobotComments,
  updateStateDrafts,
  updateStatePortedComments,
  updateStatePortedDrafts,
  updateStateDeleteDiscardedDraft,
  discardedDrafts$,
  updateStateReset,
  drafts$,
} from './comments-model';
import {changeNum$, currentPatchNum$} from '../change/change-model';
import {Finalizable} from '../registry';
import {routerChangeNum$} from '../router/router-model';
import {Interaction} from '../../constants/reporting';
import {assertIsDefined} from '../../utils/common-util';
import {debounce, DelayedTask} from '../../utils/async-util';
import {pluralize} from '../../utils/string-util';
import {ReportingService} from '../gr-reporting/gr-reporting';

const TOAST_DEBOUNCE_INTERVAL = 200;

function getSavingMessage(numPending: number, requestFailed?: boolean) {
  if (requestFailed) {
    return 'Unable to save draft';
  }
  if (numPending === 0) {
    return 'All changes saved';
  }
  return `Saving ${pluralize(numPending, 'draft')}...`;
}

export class CommentsService implements Finalizable {
  private numPendingDraftRequests = 0;

  private changeNum?: NumericChangeId;

  private patchNum?: PatchSetNum;

  private readonly reloadListener: () => void;

  private readonly subscriptions: Subscription[] = [];

  private drafts: {[path: string]: DraftInfo[]} = {};

  private draftToastTask?: DelayedTask;

  private discardedDrafts: DraftInfo[] = [];

  constructor(
    readonly restApiService: RestApiService,
    readonly reporting: ReportingService
  ) {
    this.subscriptions.push(
      discardedDrafts$.subscribe(x => (this.discardedDrafts = x))
    );
    this.subscriptions.push(drafts$.subscribe(x => (this.drafts = x ?? {})));
    this.subscriptions.push(
      currentPatchNum$.subscribe(x => (this.patchNum = x))
    );
    this.subscriptions.push(
      routerChangeNum$.subscribe(changeNum => {
        this.changeNum = changeNum;
        updateStateReset();
        this.reloadAllComments();
      })
    );
    this.subscriptions.push(
      combineLatest([changeNum$, currentPatchNum$]).subscribe(
        ([changeNum, patchNum]) => {
          this.changeNum = changeNum;
          this.patchNum = patchNum;
          this.reloadAllPortedComments();
        }
      )
    );
    this.reloadListener = () => {
      this.reloadAllComments();
      this.reloadAllPortedComments();
    };
    document.addEventListener('reload', this.reloadListener);
  }

  finalize() {
    document.removeEventListener('reload', this.reloadListener!);
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions.splice(0, this.subscriptions.length);
  }

  // Note that this does *not* reload ported comments.
  reloadAllComments() {
    if (!this.changeNum) return;
    this.reloadComments(this.changeNum);
    this.reloadRobotComments(this.changeNum);
    this.reloadDrafts(this.changeNum);
  }

  reloadAllPortedComments() {
    if (!this.changeNum) return;
    if (!this.patchNum) return;
    this.reloadPortedComments(this.changeNum, this.patchNum);
    this.reloadPortedDrafts(this.changeNum, this.patchNum);
  }

  reloadComments(changeNum: NumericChangeId): Promise<void> {
    return this.restApiService
      .getDiffComments(changeNum)
      .then(comments => updateStateComments(comments));
  }

  reloadRobotComments(changeNum: NumericChangeId): Promise<void> {
    return this.restApiService
      .getDiffRobotComments(changeNum)
      .then(robotComments => updateStateRobotComments(robotComments));
  }

  reloadDrafts(changeNum: NumericChangeId): Promise<void> {
    return this.restApiService
      .getDiffDrafts(changeNum)
      .then(drafts => updateStateDrafts(drafts));
  }

  reloadPortedComments(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    return this.restApiService
      .getPortedComments(changeNum, patchNum)
      .then(portedComments => updateStatePortedComments(portedComments));
  }

  reloadPortedDrafts(
    changeNum: NumericChangeId,
    patchNum = CURRENT as RevisionId
  ): Promise<void> {
    return this.restApiService
      .getPortedDrafts(changeNum, patchNum)
      .then(portedDrafts => updateStatePortedDrafts(portedDrafts));
  }

  async restoreDraft(id: UrlEncodedCommentId) {
    const found = this.discardedDrafts?.find(d => d.id === id);
    if (!found) throw new Error('discarded draft not found');
    const newDraft = {
      ...found,
      id: undefined,
      updated: undefined,
      __draft: undefined,
      __unsaved: true,
    };
    await this.saveDraft(newDraft);
    updateStateDeleteDiscardedDraft(id);
  }

  /**
   * Saves a new or updates an existing draft.
   * The model will only be updated when a successful response comes back.
   */
  async saveDraft(draft: DraftInfo | UnsavedInfo, showToast = true) {
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(draft.patch_set, 'patchset number of comment draft');
    if (!draft.message?.trim()) throw new Error('Cannot save empty draft.');

    // Saving the change number as to make sure that the response is still
    // relevant when it comes back. The user maybe have navigated away.
    const changeNum = this.changeNum;
    this.report(Interaction.SAVE_COMMENT, draft);
    if (showToast) this.showStartRequest();
    const result = await this.restApiService.saveDiffDraft(
      changeNum,
      draft.patch_set,
      draft
    );
    if (changeNum !== this.changeNum) throw new Error('change changed');
    if (!result.ok) {
      if (showToast) this.handleFailedDraftRequest();
      throw new Error(
        `Failed to save draft comment: ${JSON.stringify(result)}`
      );
    }
    const obj = await this.restApiService.getResponseObject(result);
    const savedComment = obj as unknown as CommentInfo;
    const updatedDraft = {
      ...draft,
      id: savedComment.id,
      updated: savedComment.updated,
      __draft: true,
      __unsaved: undefined,
    };
    if (showToast) this.showEndRequest();
    updateStateSetDraft(updatedDraft);
    this.report(Interaction.COMMENT_SAVED, updatedDraft);
  }

  async discardDraft(draftId: UrlEncodedCommentId) {
    const draft = this.lookupDraft(draftId);
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(draft, `draft not found by id ${draftId}`);
    assertIsDefined(draft.patch_set, 'patchset number of comment draft');

    if (!draft.message?.trim()) throw new Error('saved draft cant be empty');
    // Saving the change number as to make sure that the response is still
    // relevant when it comes back. The user maybe have navigated away.
    const changeNum = this.changeNum;
    this.report(Interaction.DISCARD_COMMENT, draft);
    this.showStartRequest();
    const result = await this.restApiService.deleteDiffDraft(
      changeNum,
      draft.patch_set,
      {id: draft.id}
    );
    if (changeNum !== this.changeNum) throw new Error('change changed');
    if (!result.ok) {
      this.handleFailedDraftRequest();
      throw new Error(
        `Failed to discard draft comment: ${JSON.stringify(result)}`
      );
    }
    this.showEndRequest();
    updateStateDeleteDraft(draft);
    // We don't store empty discarded drafts and don't need an UNDO then.
    if (draft.message?.trim()) {
      fire(document, 'show-alert', {
        message: 'Draft Discarded',
        action: 'Undo',
        callback: () => this.restoreDraft(draft.id),
      });
    }
    this.report(Interaction.COMMENT_DISCARDED, draft);
  }

  private report(interaction: Interaction, comment: CommentBasics) {
    const details = reportingDetails(comment);
    this.reporting.reportInteraction(interaction, details);
  }

  private showStartRequest() {
    this.numPendingDraftRequests += 1;
    this.updateRequestToast();
  }

  private showEndRequest() {
    this.numPendingDraftRequests -= 1;
    this.updateRequestToast();
  }

  private handleFailedDraftRequest() {
    this.numPendingDraftRequests -= 1;
    this.updateRequestToast(/* requestFailed=*/ true);
  }

  private updateRequestToast(requestFailed?: boolean) {
    if (this.numPendingDraftRequests === 0 && !requestFailed) {
      fireEvent(document, 'hide-alert');
      return;
    }
    const message = getSavingMessage(
      this.numPendingDraftRequests,
      requestFailed
    );
    this.draftToastTask = debounce(
      this.draftToastTask,
      () => {
        // Note: the event is fired on the body rather than this element because
        // this element may not be attached by the time this executes, in which
        // case the event would not bubble.
        fireAlert(document.body, message);
      },
      TOAST_DEBOUNCE_INTERVAL
    );
  }

  private lookupDraft(id: UrlEncodedCommentId): DraftInfo | undefined {
    return Object.values(this.drafts)
      .flat()
      .find(d => d.id === id);
  }
}
