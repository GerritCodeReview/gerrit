/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {BehaviorSubject, combineLatest, from, Observable, of} from 'rxjs';
import {catchError, map, shareReplay, switchMap} from 'rxjs/operators';
import {ChangeModel} from '../change/change-model';
import {
  changeIsAbandoned,
  changeIsMerged,
  getCurrentRevision,
} from '../../utils/change-util';
import {fireServerError} from '../../utils/event-util';
import {buildFlowCondition, getChangePrefix} from '../../utils/flows-util';
import {ChangeInfo, FlowInfo, FlowInput} from '../../api/rest-api';
import {ErrorCallback} from '../../api/rest';
import {Model} from '../base/model';
import {define} from '../dependency';
import {PluginsModel} from '../plugins/plugins-model';

import {NumericChangeId} from '../../types/common';
import {getAppContext} from '../../services/app-context';
import {FlowsAutosubmitProvider, FlowsProvider} from '../../api/flows';
import {select} from '../../utils/observable-util';
import {isDefined} from '../../types/types';

export {buildFlowCondition, getChangePrefix};

export interface FlowsState {
  isEnabled: boolean;
  flows: FlowInfo[];
  loading: boolean;
  errorMessage?: string;
  providers: FlowsProvider[];
  autosubmitProviders: FlowsAutosubmitProvider[];
}

export const flowsModelToken = define<FlowsModel>('flows-model');

export const SUBMIT_ACTION_NAME = 'submit';

export function getSubmitCondition() {
  return buildFlowCondition('is:submittable');
}

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

  readonly canCreateFlow$: Observable<boolean>;

  private canCreate = false;

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

    this.canCreateFlow$ = select(
      combineLatest([
        select(this.state$, s => s.isEnabled),
        this.changeModel.change$,
        this.changeModel.isOwner$,
      ]),
      ([isEnabled, change, isOwner]) => {
        if (
          !isEnabled ||
          !change ||
          changeIsMerged(change) ||
          changeIsAbandoned(change) ||
          !isOwner
        ) {
          return false;
        }
        const latestUploader = getCurrentRevision(change)?.uploader;
        return (
          !latestUploader ||
          latestUploader._account_id === change.owner?._account_id
        );
      }
    );

    this.subscriptions.push(
      this.canCreateFlow$.subscribe(canCreate => {
        this.canCreate = canCreate;
      })
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

  canCreateFlow(): boolean {
    return this.canCreate;
  }

  getChangePrefix(change?: Pick<ChangeInfo, 'project' | '_number'>): string {
    return getChangePrefix(change ?? this.changeModel.getState().change);
  }

  buildCondition(
    query: string,
    change?: Pick<ChangeInfo, 'project' | '_number'>
  ): string {
    return buildFlowCondition(
      query,
      change ?? this.changeModel.getState().change
    );
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
          stage.expression.condition === getSubmitCondition() &&
          stage.expression.action?.name === SUBMIT_ACTION_NAME
      )
    );
  }

  async createAutosubmitFlow() {
    if (!this.changeNum) return;
    if (!this.getState().isEnabled) return;

    // See if some plugin wants to modify the default submit behaviour
    const autosubmitProvider = this.getState().autosubmitProviders.find(
      provider => provider.getSubmitCondition && !!provider.getSubmitCondition()
    );

    const defaultAction = {
      name: SUBMIT_ACTION_NAME,
    };

    let condition = getSubmitCondition();
    if (autosubmitProvider?.getSubmitCondition()) {
      condition = `${getChangePrefix()} is ${autosubmitProvider.getSubmitCondition()}`;
    }

    await this.restApiService.createFlow(this.changeNum, {
      stage_expressions: [
        {
          condition,
          action: autosubmitProvider?.getSubmitAction() ?? defaultAction,
        },
      ],
    });

    this.reload();
  }

  async createFlow(flowInput: FlowInput, errFn?: ErrorCallback) {
    if (!this.changeNum) return;
    if (!this.getState().isEnabled) return;
    await this.restApiService.createFlow(this.changeNum, flowInput, errFn);
    this.reload();
  }

  async upsertFlow(
    existingFlowId: string | undefined,
    flowInput: FlowInput | null | undefined,
    errFn?: ErrorCallback
  ) {
    if (!this.changeNum) return;
    if (!this.getState().isEnabled) return;
    if (!existingFlowId && !flowInput) return;
    if (existingFlowId) {
      try {
        await this.restApiService.deleteFlow(this.changeNum, existingFlowId);
      } catch (err) {
        if (!flowInput) throw err;
        console.warn(`Failed to delete previous flow ${existingFlowId}:`, err);
      }
    }
    if (flowInput) {
      await this.restApiService.createFlow(this.changeNum, flowInput, errFn);
    }
    this.reload();
  }
}
