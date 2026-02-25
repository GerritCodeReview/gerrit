/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject, combineLatest, from, Observable, of} from 'rxjs';
import {catchError, map, shareReplay, switchMap} from 'rxjs/operators';
import {ChangeModel} from '../change/change-model';
import {fireServerError} from '../../utils/event-util';
import {FlowInfo, FlowInput} from '../../api/rest-api';
import {Model} from '../base/model';
import {define} from '../dependency';
import {PluginsModel} from '../plugins/plugins-model';

import {NumericChangeId} from '../../types/common';
import {getAppContext} from '../../services/app-context';
import {FlowsAutosubmitProvider, FlowsProvider} from '../../api/flows';
import {select} from '../../utils/observable-util';
import {isDefined} from '../../types/types';

export interface FlowsState {
  isEnabled: boolean;
  flows: FlowInfo[];
  loading: boolean;
  errorMessage?: string;
  providers: FlowsProvider[];
  autosubmitProviders: FlowsAutosubmitProvider[];
}

export const flowsModelToken = define<FlowsModel>('flows-model');

export const CHANGE_PREFIX = window.location.origin + window.location.pathname;
export const SUBMIT_CONDITION = CHANGE_PREFIX + ' is is:submittable';
export const SUBMIT_ACTION_NAME = 'submit';

export class FlowsModel extends Model<FlowsState> {
  readonly flows$ = this.state$.pipe(map(s => s.flows));

  readonly loading$ = this.state$.pipe(map(s => s.loading));

  readonly providers$: Observable<FlowsProvider[]> = select(
    this.state$,
    state => state.providers
  );

  readonly autosubmitProviders$: Observable<FlowsAutosubmitProvider[]> = select(
    this.state$,
    state => state.autosubmitProviders
  );

  readonly isAutosubmitEnabled$: Observable<boolean> = select(
    this.autosubmitProviders$,
    autosubmitProviders =>
      autosubmitProviders.some(
        autosubmitProvider => !!autosubmitProvider.isAutosubmitEnabled()
      )
  );

  readonly enabled$: Observable<boolean>;

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  private changeNum?: NumericChangeId;

  private readonly restApiService = getAppContext().restApiService;

  constructor(
    private readonly changeModel: ChangeModel,
    private readonly pluginsModel: PluginsModel
  ) {
    super({
      isEnabled: false,
      flows: [],
      loading: true,
      providers: [],
      autosubmitProviders: [],
    });

    this.enabled$ = this.changeModel.changeNum$.pipe(
      switchMap(changeNum => {
        if (!changeNum) {
          return of(false);
        }
        const errFn = (response?: Response | null) => {
          // When 404 is returned, it means that flows are not enabled.
          if (response?.status === 404) return;
          if (!response) return;
          fireServerError(response);
        };
        return from(
          this.restApiService.getIfFlowsIsEnabled(changeNum, errFn)
        ).pipe(
          map(res => res?.enabled ?? false),
          catchError(() => of(false))
        );
      }),
      shareReplay(1)
    );

    this.subscriptions.push(
      this.enabled$.subscribe(isEnabled => {
        this.setState({...this.getState(), isEnabled});
      })
    );

    this.subscriptions.push(
      this.changeModel.changeNum$.subscribe(changeNum => {
        this.changeNum = changeNum;
      })
    );

    this.subscriptions.push(
      combineLatest([this.changeModel.changeNum$, this.reload$, this.enabled$])
        .pipe(
          switchMap(([changeNum, _, enabled]) => {
            if (!changeNum || !enabled) return of([]);
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

    this.pluginsModel.flowsAutosubmitPlugin$.subscribe(plugins => {
      const providers = plugins.map(p => p.provider).filter(isDefined);
      this.updateState({
        autosubmitProviders: providers,
      });
    });

    this.pluginsModel.flowsPlugins$.subscribe(plugins => {
      const providers = plugins.map(p => p.provider).filter(isDefined);
      this.updateState({
        providers,
      });
    });
  }

  reload() {
    this.reload$.next();
  }

  async deleteFlow(flowId: string) {
    if (!this.changeNum) return;
    if (!this.getState().isEnabled) return;
    await this.restApiService.deleteFlow(this.changeNum, flowId);
    this.reload();
  }

  hasAutosubmitFlowAlready() {
    return this.getState().flows.some(flow =>
      flow.stages.some(
        stage =>
          stage.expression.condition === SUBMIT_CONDITION &&
          stage.expression.action?.name === SUBMIT_ACTION_NAME
      )
    );
  }

  async createAutosubmitFlow() {
    if (!this.changeNum) return;
    if (!this.getState().isEnabled) return;
    await this.restApiService.createFlow(this.changeNum, {
      stage_expressions: [
        {
          condition: SUBMIT_CONDITION,
          action: {
            name: SUBMIT_ACTION_NAME,
          },
        },
      ],
    });
    this.reload();
  }

  async createFlow(flowInput: FlowInput) {
    if (!this.changeNum) return;
    if (!this.getState().isEnabled) return;
    await this.restApiService.createFlow(this.changeNum, flowInput);
    this.reload();
  }
}
