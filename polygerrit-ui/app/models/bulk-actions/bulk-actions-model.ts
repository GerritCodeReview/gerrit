/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
  GroupInfo,
  Hashtag,
  NumericChangeId,
  ReviewerState,
} from '../../api/rest-api';
import {Model} from '../base/model';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {define} from '../dependency';
import {select} from '../../utils/observable-util';
import {
  AttentionSetInput,
  RelatedChangeAndCommitInfo,
  ReviewerInput,
  ReviewInput,
  ReviewResult,
} from '../../types/common';
import {getUserId} from '../../utils/account-util';
import {getChangeNumber, isChangeInfo} from '../../utils/change-util';
import {deepEqual} from '../../utils/deep-util';
import {throwingErrorCallback} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {assert} from '../../utils/common-util';

export const bulkActionsModelToken =
  define<BulkActionsModel>('bulk-actions-model');

export enum LoadingState {
  NOT_SYNCED = 'NOT_SYNCED',
  LOADING = 'LOADING',
  LOADED = 'LOADED',
}
export interface BulkActionsState {
  loadingState: LoadingState;
  selectableChangeNums: NumericChangeId[];
  selectedChangeNums: NumericChangeId[];
  allChanges: Map<NumericChangeId, ChangeInfo>;
}

const initialState: BulkActionsState = {
  loadingState: LoadingState.NOT_SYNCED,
  selectedChangeNums: [],
  selectableChangeNums: [],
  allChanges: new Map(),
};

export class BulkActionsModel extends Model<BulkActionsState> {
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
    if (!current.selectableChangeNums.includes(changeNum)) {
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
    if (!current.selectableChangeNums.includes(changeNum)) {
      throw new Error(
        `Trying to remove change ${changeNum} that is not part of bulk-actions model`
      );
    }
    const selectedChangeNums = [...current.selectedChangeNums];
    const index = selectedChangeNums.findIndex(item => item === changeNum);
    if (index === -1) return;
    selectedChangeNums.splice(index, 1);
    this.updateState({selectedChangeNums});
  }

  clearSelectedChangeNums() {
    this.updateState({selectedChangeNums: []});
  }

  selectAll() {
    const current = this.getState();
    this.updateState({
      selectedChangeNums: Array.from(current.allChanges.keys()),
    });
  }

  abandonChanges(
    reason?: string,
    // errorFn is needed to avoid showing an error dialog
    errFn?: (changeNum: NumericChangeId) => void
  ): Promise<Response>[] {
    const current = this.getState();
    return current.selectedChangeNums.map(changeNum => {
      if (!current.allChanges.get(changeNum))
        throw new Error('invalid change id');
      const change = current.allChanges.get(changeNum)!;
      if (change.status === ChangeStatus.ABANDONED) {
        return Promise.resolve(new Response());
      }
      return this.restApiService.executeChangeAction(
        getChangeNumber(change),
        change.actions!.abandon!.method,
        '/abandon',
        undefined,
        {message: reason ?? ''},
        () => errFn && errFn(getChangeNumber(change))
      );
    });
  }

  voteChanges(reviewInput: ReviewInput) {
    const current = this.getState();
    return current.selectedChangeNums.map(changeNum => {
      const change = current.allChanges.get(changeNum)!;
      if (!change) throw new Error('invalid change id');
      return this.restApiService.saveChangeReview(
        getChangeNumber(change),
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
  ): Promise<ReviewResult | undefined>[] {
    const current = this.getState();
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
        return Promise.resolve(undefined);
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
        getChangeNumber(change),
        'current',
        reviewInput
      );
    });
  }

  addHashtags(hashtags: Hashtag[]): Promise<Hashtag[]>[] {
    const current = this.getState();
    return current.selectedChangeNums.map(changeNum =>
      this.restApiService
        .setChangeHashtag(
          changeNum,
          {
            add: hashtags,
          },
          throwingErrorCallback
        )
        .then(responseHashtags => {
          // With throwingErrorCallback guaranteed to be non-null.
          assert(!!responseHashtags, 'setChangeHastag returned undefined');
          // Once we get server confirmation that the hashtags were added to the
          // change, we are updating the model's ChangeInfo. This way we can
          // keep the page state (dialog status) but use the updated change info
          // naturally.

          // refetch the current state since other changes may have been updated
          // since the promises were launched.
          const current = this.getState();
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

  async sync(changes: (ChangeInfo | RelatedChangeAndCommitInfo)[]) {
    const basicChanges = new Map(changes.map(c => [getChangeNumber(c), c]));
    let currentState = this.getState();
    const selectedChangeNums = currentState.selectedChangeNums.filter(
      changeNum => basicChanges.has(changeNum)
    );
    const selectableChangeNums = changes.map(c => getChangeNumber(c));
    this.updateState({
      loadingState: LoadingState.LOADING,
      selectedChangeNums,
      selectableChangeNums,
      allChanges: new Map(),
    });

    if (changes.length === 0) {
      return;
    }

    // Don't ask for SUBMIT_REQUIREMENTS if it is already available.
    const needsSubmitRequirements = !this.hasSubmitRequirements(changes[0]);
    const changeDetails =
      await this.restApiService.getDetailedChangesWithActions(
        changes.map(c => getChangeNumber(c)),
        needsSubmitRequirements
      );
    currentState = this.getState();
    // Return early if sync has been called again since starting the load.
    if (!deepEqual(selectableChangeNums, currentState.selectableChangeNums)) {
      return;
    }
    const allDetailedChanges: Map<NumericChangeId, ChangeInfo> = new Map();
    for (const detailedChange of changeDetails ?? []) {
      allDetailedChanges.set(detailedChange._number, {
        ...detailedChange,
        submit_requirements: needsSubmitRequirements
          ? detailedChange.submit_requirements
          : (basicChanges.get(detailedChange._number) as ChangeInfo)
              ?.submit_requirements,
      });
    }
    this.setState({
      ...currentState,
      loadingState: LoadingState.LOADED,
      allChanges: allDetailedChanges,
    });
  }

  private hasSubmitRequirements(
    change: ChangeInfo | RelatedChangeAndCommitInfo
  ): boolean {
    return isChangeInfo(change) && change.submit_requirements !== undefined;
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
