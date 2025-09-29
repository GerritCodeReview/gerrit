/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject, combineLatest, from, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {ChangeModel} from '../change/change-model';
import {FlowInfo} from '../../api/rest-api';
import {Model} from '../base/model';
import {define} from '../dependency';

import {NumericChangeId} from '../../types/common';
import {getAppContext} from '../../services/app-context';

export interface FlowsState {
  flows: FlowInfo[];
  loading: boolean;
  errorMessage?: string;
}

export const flowsModelToken = define<FlowsModel>('flows-model');

export class FlowsModel extends Model<FlowsState> {
  readonly flows$ = this.state$.pipe(map(s => s.flows));

  readonly loading$ = this.state$.pipe(map(s => s.loading));

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  private changeNum?: NumericChangeId;

  private readonly restApiService = getAppContext().restApiService;

  constructor(private readonly changeModel: ChangeModel) {
    super({
      flows: [],
      loading: true,
    });

    this.subscriptions.push(
      this.changeModel.changeNum$.subscribe(changeNum => {
        this.changeNum = changeNum;
      })
    );

    this.subscriptions.push(
      combineLatest([this.changeModel.changeNum$, this.reload$])
        .pipe(
          switchMap(([changeNum]) => {
            if (!changeNum) return of([]);
            this.setState({...this.getState(), loading: true});
            return from(this.restApiService.listFlows(changeNum)).pipe(
              catchError(err => {
                this.setState({
                  ...this.getState(),
                  errorMessage: `Failed to load flows: ${err}`,
                  loading: false,
                });
                return of([]);
              })
            );
          })
        )
        .subscribe(flows => {
          this.setState({
            ...this.getState(),
            flows: flows ?? [],
            loading: false,
          });
        })
    );
  }

  reload() {
    this.reload$.next();
  }

  async deleteFlow(flowId: string) {
    if (!this.changeNum) return;
    await this.restApiService.deleteFlow(this.changeNum, flowId);
    this.reload();
  }
}
