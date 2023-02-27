/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  ChangeInfo,
  RelatedChangeAndCommitInfo,
  SubmittedTogetherInfo,
} from '../../types/common';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {select} from '../../utils/observable-util';
import {Model} from '../model';
import {define} from '../dependency';
import {ChangeModel} from './change-model';
import {combineLatest, from, of} from 'rxjs';
import {switchMap} from 'rxjs/operators';
import {changeIsOpen} from '../../utils/change-util';
import {ConfigModel} from '../config/config-model';

export interface RelatedChangesState {
  /** `undefined` means "not yet loaded". */
  relatedChanges?: RelatedChangeAndCommitInfo[];
  submittedTogether?: SubmittedTogetherInfo;
  cherryPicks?: ChangeInfo[];
  conflictingChanges?: ChangeInfo[];
  sameTopicChanges?: ChangeInfo[];
}

const initialState: RelatedChangesState = {
  relatedChanges: undefined,
  submittedTogether: undefined,
  cherryPicks: undefined,
  conflictingChanges: undefined,
  sameTopicChanges: undefined,
};

export const relatedChangesModelToken = define<RelatedChangesModel>(
  'related-changes-model'
);

export class RelatedChangesModel extends Model<RelatedChangesState> {
  public readonly relatedChanges$ = select(
    this.state$,
    state => state.relatedChanges
  );

  public readonly submittedTogether$ = select(
    this.state$,
    state => state.submittedTogether
  );

  public readonly cherryPicks$ = select(
    this.state$,
    state => state.cherryPicks
  );

  public readonly conflictingChanges$ = select(
    this.state$,
    state => state.conflictingChanges
  );

  public readonly sameTopicChanges$ = select(
    this.state$,
    state => state.sameTopicChanges
  );

  /**
   * Determines whether the change has a parent change. If there
   * is a relation chain, and the change id is not the last item of the
   * relation chain, then there is a parent.
   */
  public readonly hasParent$ = select(
    combineLatest([this.changeModel.change$, this.relatedChanges$]),
    ([change, relatedChanges]) => {
      if (!change) return undefined;
      if (relatedChanges === undefined) return undefined;
      if (relatedChanges.length === 0) return false;
      const lastChangeId = relatedChanges[relatedChanges.length - 1].change_id;
      return lastChangeId !== change.change_id;
    }
  );

  constructor(
    readonly changeModel: ChangeModel,
    readonly configModel: ConfigModel,
    readonly restApiService: RestApiService
  ) {
    super(initialState);
    this.subscriptions = [
      this.loadRelatedChanges(),
      this.loadSubmittedTogether(),
      this.loadCherryPicks(),
      this.loadConflictingChanges(),
      this.loadSameTopicChanges(),
    ];
  }

  private loadRelatedChanges() {
    return combineLatest([
      this.changeModel.reload$,
      this.changeModel.changeNum$,
      this.changeModel.latestPatchNum$,
    ])
      .pipe(
        switchMap(([_, changeNum, latestPatchNum]) => {
          if (!changeNum || !latestPatchNum) return of(undefined);
          return from(
            this.restApiService
              .getRelatedChanges(changeNum, latestPatchNum)
              .then(info => info?.changes ?? [])
          );
        })
      )
      .subscribe(relatedChanges => {
        this.updateState({relatedChanges});
      });
  }

  private loadSubmittedTogether() {
    return combineLatest([
      this.changeModel.reload$,
      this.changeModel.changeNum$,
    ])
      .pipe(
        switchMap(([_, changeNum]) => {
          if (!changeNum) return of(undefined);
          return from(
            this.restApiService.getChangesSubmittedTogether(changeNum)
          );
        })
      )
      .subscribe(submittedTogether => {
        this.updateState({submittedTogether});
      });
  }

  private loadCherryPicks() {
    return combineLatest([this.changeModel.reload$, this.changeModel.change$])
      .pipe(
        switchMap(([_, change]) => {
          if (!change) return of(undefined);
          return from(
            this.restApiService.getChangeCherryPicks(
              change.project,
              change.change_id,
              change._number
            )
          );
        })
      )
      .subscribe(cherryPicks => {
        this.updateState({cherryPicks});
      });
  }

  private loadConflictingChanges() {
    return combineLatest([
      this.changeModel.reload$,
      this.changeModel.change$,
      this.changeModel.mergeable$,
    ])
      .pipe(
        switchMap(([_, change, mergeable]) => {
          if (!change || !mergeable) return of(undefined);
          if (!changeIsOpen(change)) return of(undefined);
          return from(this.restApiService.getChangeConflicts(change._number));
        })
      )
      .subscribe(conflictingChanges => {
        this.updateState({conflictingChanges});
      });
  }

  private loadSameTopicChanges() {
    return combineLatest([
      this.changeModel.reload$,
      this.changeModel.change$,
      this.configModel.serverConfig$,
    ])
      .pipe(
        switchMap(([_, change, config]) => {
          if (!change?.topic || !config) return of(undefined);
          if (config.change.submit_whole_topic) return of(undefined);
          return from(
            this.restApiService.getChangesWithSameTopic(change.topic, {
              openChangesOnly: true,
              changeToExclude: change._number,
            })
          );
        })
      )
      .subscribe(sameTopicChanges => {
        this.updateState({sameTopicChanges});
      });
  }
}
