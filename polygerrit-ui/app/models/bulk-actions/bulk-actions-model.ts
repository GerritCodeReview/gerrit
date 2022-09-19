/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ChangeInfo,
  NumericChangeId,
  ChangeStatus,
  ReviewerState,
  AccountId,
  AccountInfo,
  GroupInfo,
  Hashtag,
} from '../../api/rest-api';
import {Model} from '../model';
import {Finalizable} from '../../services/registry';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {define} from '../dependency';
import {select} from '../../utils/observable-util';
import {
  ReviewInput,
  ReviewerInput,
  AttentionSetInput,
} from '../../types/common';
import {getUserId} from '../../utils/account-util';

export const bulkActionsModelToken =
  define<BulkActionsModel>('bulk-actions-model');

export enum LoadingState {
  NOT_SYNCED = 'NOT_SYNCED',
  LOADING = 'LOADING',
  LOADED = 'LOADED',
}
export interface BulkActionsState {
  loadingState: LoadingState;
  selectedChangeNums: NumericChangeId[];
  allChanges: Map<NumericChangeId, ChangeInfo>;
}

const initialState: BulkActionsState = {
  loadingState: LoadingState.NOT_SYNCED,
  selectedChangeNums: [],
  allChanges: new Map(),
};

export class BulkActionsModel
  extends Model<BulkActionsState>
  implements Finalizable
{
  constructor(private readonly restApiService: RestApiService) {
    super(initialState);
  }

  public readonly selectedChangeNums$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.selectedChangeNums
  );

  public readonly totalChangeCount$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.allChanges.size
  );

  public readonly loadingState$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.loadingState
  );

  public readonly allChanges$ = select(
    this.state$,
    bulkActionsState => bulkActionsState.allChanges
  );

  public readonly selectedChanges$ = select(this.state$, bulkActionsState => {
    const result = [];
    for (const changeNum of bulkActionsState.selectedChangeNums) {
      const change = bulkActionsState.allChanges.get(changeNum);
      if (change) result.push(change);
    }
    return result;
  });

  toggleSelectedChangeNum(changeNum: NumericChangeId) {
    this.getState().selectedChangeNums.includes(changeNum)
      ? this.removeSelectedChangeNum(changeNum)
      : this.addSelectedChangeNum(changeNum);
  }

  addSelectedChangeNum(changeNum: NumericChangeId) {
    const current = this.getState();
    if (!current.allChanges.has(changeNum)) {
      throw new Error(
        `Trying to add change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const selectedChangeNums = [...current.selectedChangeNums];
    selectedChangeNums.push(changeNum);
    this.setState({...current, selectedChangeNums});
  }

  removeSelectedChangeNum(changeNum: NumericChangeId) {
    const current = this.getState();
    if (!current.allChanges.has(changeNum)) {
      throw new Error(
        `Trying to remove change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const selectedChangeNums = [...current.selectedChangeNums];
    const index = selectedChangeNums.findIndex(item => item === changeNum);
    if (index === -1) return;
    selectedChangeNums.splice(index, 1);
    this.setState({...current, selectedChangeNums});
  }

  clearSelectedChangeNums() {
    this.setState({...this.subject$.getValue(), selectedChangeNums: []});
  }

  selectAll() {
    const current = this.subject$.getValue();
    this.setState({
      ...current,
      selectedChangeNums: Array.from(current.allChanges.keys()),
    });
  }

  abandonChanges(
    reason?: string,
    // errorFn is needed to avoid showing an error dialog
    errFn?: (changeNum: NumericChangeId) => void
  ): Promise<Response | undefined>[] {
    const current = this.subject$.getValue();
    return current.selectedChangeNums.map(changeNum => {
      if (!current.allChanges.get(changeNum))
        throw new Error('invalid change id');
      const change = current.allChanges.get(changeNum)!;
      if (change.status === ChangeStatus.ABANDONED) {
        return Promise.resolve(new Response());
      }
      return this.restApiService.executeChangeAction(
        change._number,
        change.actions!.abandon!.method,
        '/abandon',
        undefined,
        {message: reason ?? ''},
        () => errFn && errFn(change._number)
      );
    });
  }

  voteChanges(reviewInput: ReviewInput) {
    const current = this.subject$.getValue();
    return current.selectedChangeNums.map(changeNum => {
      const change = current.allChanges.get(changeNum)!;
      if (!change) throw new Error('invalid change id');
      return this.restApiService.saveChangeReview(
        change._number,
        'current',
        reviewInput,
        () => {
          throw new Error();
        }
      );
    });
  }

  addReviewers(
    changedReviewers: Map<ReviewerState, (AccountInfo | GroupInfo)[]>,
    reason: string
  ): Promise<Response>[] {
    const current = this.subject$.getValue();
    const changes = current.selectedChangeNums.map(
      changeNum => current.allChanges.get(changeNum)!
    );
    return changes.map(change => {
      const reviewersNewToChange: ReviewerInput[] = [
        ReviewerState.REVIEWER,
        ReviewerState.CC,
      ].flatMap(state =>
        this.getNewReviewersToChange(change, state, changedReviewers)
      );
      if (reviewersNewToChange.length === 0) {
        return Promise.resolve(new Response());
      }
      const attentionSetUpdates: AttentionSetInput[] = reviewersNewToChange
        .filter(reviewerInput => reviewerInput.state === ReviewerState.REVIEWER)
        .map(reviewerInput => {
          return {
            // TODO: Once Groups are supported, filter them out and only add
            // Accounts to the attention set, just like gr-reply-dialog.
            user: reviewerInput.reviewer as AccountId,
            reason,
          };
        });
      const reviewInput: ReviewInput = {
        reviewers: reviewersNewToChange,
        ignore_automatic_attention_set_rules: true,
        add_to_attention_set: attentionSetUpdates,
      };
      return this.restApiService.saveChangeReview(
        change._number,
        'current',
        reviewInput
      );
    });
  }

  addHashtags(hashtags: Hashtag[]): Promise<Hashtag[]>[] {
    const current = this.subject$.getValue();
    return current.selectedChangeNums.map(changeNum =>
      this.restApiService
        .setChangeHashtag(changeNum, {
          add: hashtags,
        })
        .then(responseHashtags => {
          // Once we get server confirmation that the hashtags were added to the
          // change, we are updating the model's ChangeInfo. This way we can
          // keep the page state (dialog status) but use the updated change info
          // naturally.

          // refetch the current state since other changes may have been updated
          // since the promises were launched.
          const current = this.subject$.getValue();
          const nextState = {
            ...current,
            allChanges: new Map(current.allChanges),
          };
          nextState.allChanges.set(changeNum, {
            ...nextState.allChanges.get(changeNum)!,
            hashtags: responseHashtags,
          });
          this.setState(nextState);
          return responseHashtags;
        })
    );
  }

  async sync(changes: ChangeInfo[]) {
    const basicChanges = new Map(changes.map(c => [c._number, c]));
    let currentState = this.subject$.getValue();
    const selectedChangeNums = currentState.selectedChangeNums.filter(
      changeNum => basicChanges.has(changeNum)
    );
    this.setState({
      ...currentState,
      loadingState: LoadingState.LOADING,
      selectedChangeNums,
      allChanges: basicChanges,
    });

    if (changes.length === 0) {
      return;
    }
    const changeDetails =
      await this.restApiService.getDetailedChangesWithActions(
        changes.map(c => c._number)
      );
    currentState = this.subject$.getValue();
    // Return early if sync has been called again since starting the load.
    if (basicChanges !== currentState.allChanges) return;
    const allDetailedChanges: Map<NumericChangeId, ChangeInfo> = new Map();
    for (const detailedChange of changeDetails ?? []) {
      const basicChange = basicChanges.get(detailedChange._number)!;
      allDetailedChanges.set(
        detailedChange._number,
        this.mergeOldAndDetailedChangeInfos(basicChange, detailedChange)
      );
    }
    this.setState({
      ...currentState,
      loadingState: LoadingState.LOADED,
      allChanges: allDetailedChanges,
    });
  }

  /** Required for testing */
  getState() {
    return this.subject$.getValue();
  }

  setState(state: BulkActionsState) {
    this.subject$.next(state);
  }

  private mergeOldAndDetailedChangeInfos(
    originalChange: ChangeInfo,
    newData: ChangeInfo
  ) {
    return {
      ...originalChange,
      ...newData,
      reviewers: originalChange.reviewers,
    };
  }

  private getNewReviewersToChange(
    change: ChangeInfo,
    state: ReviewerState,
    changedReviewers: Map<ReviewerState, (AccountInfo | GroupInfo)[]>
  ): ReviewerInput[] {
    return (
      changedReviewers
        .get(state)
        ?.filter(account => !change.reviewers[state]?.includes(account))
        .map(account => {
          return {state, reviewer: getUserId(account)};
        }) ?? []
    );
  }
}
