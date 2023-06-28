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
import {combineLatest, forkJoin, from, of} from 'rxjs';
import {map, switchMap} from 'rxjs/operators';
import {ConfigModel} from '../config/config-model';
import {ChangeStatus} from '../../api/rest-api';
import {isDefined} from '../../types/types';

export interface RelatedChangesState {
  /** `undefined` means "not yet loaded". */
  relatedChanges?: RelatedChangeAndCommitInfo[];
  submittedTogether?: SubmittedTogetherInfo;
  cherryPicks?: ChangeInfo[];
  conflictingChanges?: ChangeInfo[];
  sameTopicChanges?: ChangeInfo[];
  revertingChanges: ChangeInfo[];
}

const initialState: RelatedChangesState = {
  relatedChanges: undefined,
  submittedTogether: undefined,
  cherryPicks: undefined,
  conflictingChanges: undefined,
  sameTopicChanges: undefined,
  revertingChanges: [],
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
   * Emits all changes that have reverted the current change, based on
   * information from parsed change messages. Abandoned changes are not
   * included.
   */
  public readonly revertingChanges$ = select(
    this.state$,
    state => state.revertingChanges
  );

  /**
   * Emits one reverting change (if there is any) from revertingChanges$.
   * It prefers MERGED changes. Otherwise the choice is random.
   */
  public readonly revertingChange$ = select(
    this.revertingChanges$,
    revertingChanges => {
      if (revertingChanges.length === 0) return undefined;
      const submittedRevert = revertingChanges.find(
        c => c.status === ChangeStatus.MERGED
      );
      if (submittedRevert) return submittedRevert;
      return revertingChanges[0];
    }
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
      this.loadRevertingChanges(),
    ];
  }

  private loadRelatedChanges() {
    return combineLatest([
      this.changeModel.changeNum$,
      this.changeModel.latestPatchNum$,
    ])
      .pipe(
        switchMap(([changeNum, latestPatchNum]) => {
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
    return this.changeModel.changeNum$
      .pipe(
        switchMap(changeNum => {
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
    return combineLatest([
      this.changeModel.branch$,
      this.changeModel.changeId$,
      this.changeModel.repo$,
    ])
      .pipe(
        switchMap(([branch, changeId, repo]) => {
          if (!branch || !changeId || !repo) return of(undefined);
          return from(
            this.restApiService.getChangeCherryPicks(repo, changeId, branch)
          );
        })
      )
      .subscribe(cherryPicks => {
        this.updateState({cherryPicks});
      });
  }

  private loadConflictingChanges() {
    return combineLatest([
      this.changeModel.changeNum$,
      this.changeModel.status$,
      this.changeModel.mergeable$,
    ])
      .pipe(
        switchMap(([changeNum, status, mergeable]) => {
          if (!changeNum || !status || !mergeable) return of(undefined);
          if (status !== ChangeStatus.NEW) return of(undefined);
          return from(this.restApiService.getChangeConflicts(changeNum));
        })
      )
      .subscribe(conflictingChanges => {
        this.updateState({conflictingChanges});
      });
  }

  private loadSameTopicChanges() {
    return combineLatest([
      this.changeModel.changeNum$,
      this.changeModel.topic$,
      this.configModel.serverConfig$,
    ])
      .pipe(
        switchMap(([changeNum, topic, config]) => {
          if (!changeNum || !topic || !config) return of(undefined);
          if (config.change.submit_whole_topic) return of(undefined);
          return from(
            this.restApiService.getChangesWithSameTopic(topic, {
              openChangesOnly: true,
              changeToExclude: changeNum,
            })
          );
        })
      )
      .subscribe(sameTopicChanges => {
        this.updateState({sameTopicChanges});
      });
  }

  private loadRevertingChanges() {
    return this.changeModel.revertingChangeIds$
      .pipe(
        switchMap(changeIds => {
          if (!changeIds?.length) return of([]);
          return forkJoin(
            changeIds.map(changeId =>
              from(this.restApiService.getChange(changeId))
            )
          );
        }),
        map(changes => changes.filter(isDefined)),
        map(changes => changes.filter(c => c.status !== ChangeStatus.ABANDONED))
      )
      .subscribe(revertingChanges => {
        this.updateState({revertingChanges});
      });
  }
}
