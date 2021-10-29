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
import {fire, fireAlert} from '../../utils/event-util';
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
import {combineLatest} from 'rxjs';
import {Interaction} from '../../constants/reporting';
import {assertIsDefined} from '../../utils/common-util';
import {debounce, DelayedTask} from '../../utils/async-util';
import {pluralize} from '../../utils/string-util';
import {ReportingService} from '../gr-reporting/gr-reporting';

const TOAST_DEBOUNCE_INTERVAL = 200;

const SAVED_MESSAGE = 'All changes saved';
const UNSAVED_MESSAGE = 'Unable to save draft';

function getSavingMessage(numPending: number, requestFailed?: boolean) {
  if (requestFailed) {
    return UNSAVED_MESSAGE;
  }
  if (numPending === 0) {
    return SAVED_MESSAGE;
  }
  return `Saving ${pluralize(numPending, 'draft')}...`;
}

export class CommentsService {
  private numPendingDraftRequests: {number: number} = {number: 0};

  private changeNum?: NumericChangeId;

  private patchNum?: PatchSetNum;

  private drafts: {[path: string]: DraftInfo[]} = {};

  private draftToastTask?: DelayedTask;

  private discardedDrafts: DraftInfo[] = [];

  constructor(
    readonly restApiService: RestApiService,
    readonly reporting: ReportingService
  ) {
    discardedDrafts$.subscribe(x => (this.discardedDrafts = x));
    drafts$.subscribe(x => (this.drafts = x));
    currentPatchNum$.subscribe(x => (this.patchNum = x));
    changeNum$.subscribe(changeNum => {
      this.changeNum = changeNum;
      console.log(`change-service load comments for change ${changeNum}`);
      updateStateReset();
      this.reloadAllComments();
    });
    combineLatest([changeNum$, currentPatchNum$]).subscribe(
      ([changeNum, patchNum]) => {
        this.changeNum = changeNum;
        this.patchNum = patchNum;
        this.reloadAllPortedComments();
      }
    );
    document.addEventListener('reload', () => {
      this.reloadAllComments();
      this.reloadAllPortedComments();
    });
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

  restoreDraft(id: UrlEncodedCommentId) {
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(this.patchNum, 'patchset number');

    const found = this.discardedDrafts?.find(d => d.id === id);
    if (!found) throw new Error('discarded draft not found');
    const newDraft = {
      ...found,
      id: undefined,
    };
    const changeNum = this.changeNum;
    const patchNum = this.patchNum;
    this.restApiService
      .saveDiffDraft(changeNum, patchNum, newDraft)
      .then(result => {
        if (!result.ok) {
          fireAlert(document, 'Unable to restore draft');
          return;
        }
        this.restApiService.getResponseObject(result).then(obj => {
          const savedComment = obj as unknown as CommentInfo;
          updateStateSetDraft({
            ...newDraft,
            ...savedComment,
            __draft: true,
          });
          updateStateDeleteDiscardedDraft(id);
        });
      });
  }

  /**
   * Saves a new or updates an existing draft.
   * The model will only be updated when a successful response comes back.
   */
  saveDraft(draft: DraftInfo | UnsavedInfo) {
    console.log(
      `service.saveDraft: ${draft.id} ${draft.message} ${draft.unresolved}`
    );
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(this.patchNum, 'patchset number');
    if (!draft.message?.trim()) throw new Error('Cannot save empty draft.');

    this.showStartRequest();
    const changeNum = this.changeNum;
    const patchNum = this.patchNum;
    this.report(Interaction.SAVE_COMMENT, draft);
    return this.restApiService
      .saveDiffDraft(changeNum, patchNum, draft)
      .then(result => {
        if (changeNum !== this.changeNum) throw new Error('change changed');
        if (!result.ok) {
          throw new Error(
            `Failed to save draft comment: ${JSON.stringify(result)}`
          );
        }
        this.showEndRequest();
        return this.restApiService.getResponseObject(result).then(obj => {
          const savedComment = obj as unknown as CommentInfo;
          const updatedDraft = {
            ...draft,
            id: savedComment.id,
            updated: savedComment.updated,
            __draft: true,
            __unsaved: undefined,
          };
          updateStateSetDraft(updatedDraft);
          this.report(Interaction.COMMENT_SAVED, updatedDraft);
        });
      })
      .catch(err => {
        this.handleFailedDraftRequest();
        throw err;
      });
  }

  discardDraft(draftId: UrlEncodedCommentId) {
    console.log(`service.discardDraft: ${draftId}`);
    const draft = this.lookupDraft(draftId);
    assertIsDefined(this.changeNum, 'change number');
    assertIsDefined(this.patchNum, 'patchset number');
    assertIsDefined(draft, `draft not found by id ${draftId}`);

    if (!draft.message?.trim()) throw new Error('saved draft cant be empty');
    fireAlert(document, 'Discarding draft...');
    const changeNum = this.changeNum;
    const patchNum = this.patchNum;
    this.report(Interaction.SAVE_COMMENT, draft);
    return this.restApiService
      .deleteDiffDraft(changeNum, patchNum, {id: draft.id})
      .then(result => {
        if (changeNum !== this.changeNum) throw new Error('change changed');
        if (!result.ok) {
          throw new Error(
            `Failed to discard draft comment: ${JSON.stringify(result)}`
          );
        }
        updateStateDeleteDraft(draft);
        // We don't store empty discarded drafts and don't need an UNDO then.
        if (draft.message?.trim()) {
          fire(document, 'show-alert', {
            message: 'Draft Discarded',
            action: 'Undo',
            callback: () => this.restoreDraft(draft.id),
          });
        }
        this.report(Interaction.COMMENT_SAVED, draft);
      });
  }

  private report(interaction: Interaction, comment: CommentBasics) {
    const details = reportingDetails(comment);
    this.reporting.reportInteraction(interaction, details);
  }

  private showStartRequest() {
    const numPending = ++this.numPendingDraftRequests.number;
    this.updateRequestToast(numPending);
  }

  private showEndRequest() {
    const numPending = --this.numPendingDraftRequests.number;
    this.updateRequestToast(numPending);
  }

  private handleFailedDraftRequest() {
    this.numPendingDraftRequests.number--;

    // Cancel the debouncer so that error toasts from the error-manager will
    // not be overridden.
    this.draftToastTask?.cancel();
    this.updateRequestToast(
      this.numPendingDraftRequests.number,
      /* requestFailed=*/ true
    );
  }

  private updateRequestToast(numPending: number, requestFailed?: boolean) {
    const message = getSavingMessage(numPending, requestFailed);
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
