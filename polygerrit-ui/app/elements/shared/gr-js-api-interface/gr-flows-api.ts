/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {combineLatest} from 'rxjs';
import {
  FlowsAutosubmitProvider,
  FlowsPluginApi,
  FlowsProvider,
} from '../../../api/flows';
import {ErrorCallback} from '../../../api/rest';
import {ChangeInfo, FlowInfo, FlowInput} from '../../../api/rest-api';
import {FlowsModel} from '../../../models/flows/flows-model';
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {buildFlowCondition, getChangePrefix} from '../../../utils/flows-util';
import {Plugin} from './gr-public-js-api';

export class GrFlowsApi implements FlowsPluginApi {
  constructor(
    private readonly plugins: PluginsModel,
    private readonly plugin: Plugin,
    private readonly getFlowsModel?: () => FlowsModel
  ) {}

  register(provider: FlowsProvider): void {
    this.plugins.registerFlowsProvider({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }

  registerAutosubmitProvider(provider: FlowsAutosubmitProvider): void {
    this.plugins.registerFlowsAutosubmitProvider({
      pluginName: this.plugin.getPluginName(),
      provider,
    });
  }

  isEnabled(): boolean {
    return this.getFlowsModel?.().getState().isEnabled ?? false;
  }

  canCreateFlow(): boolean {
    return this.getFlowsModel?.().canCreateFlow() ?? false;
  }

  getFlows(): FlowInfo[] {
    return this.getFlowsModel?.().getState().flows ?? [];
  }

  isLoading(): boolean {
    return this.getFlowsModel?.().getState().loading ?? false;
  }

  getChangePrefix(change?: Pick<ChangeInfo, 'project' | '_number'>): string {
    return (
      this.getFlowsModel?.().getChangePrefix(change) ?? getChangePrefix(change)
    );
  }

  buildCondition(
    query: string,
    change?: Pick<ChangeInfo, 'project' | '_number'>
  ): string {
    return (
      this.getFlowsModel?.().buildCondition(query, change) ??
      buildFlowCondition(query, change)
    );
  }

  subscribe(listener: () => void): () => void {
    const flowsModel = this.getFlowsModel?.();
    if (!flowsModel) {
      return () => {};
    }
    const sub = combineLatest([
      flowsModel.state$,
      flowsModel.canCreateFlow$,
    ]).subscribe(() => listener());
    return () => sub.unsubscribe();
  }

  async createFlow(flowInput: FlowInput, errFn?: ErrorCallback): Promise<void> {
    await this.getFlowsModel?.().createFlow(flowInput, errFn);
  }

  async deleteFlow(flowId: string): Promise<void> {
    await this.getFlowsModel?.().deleteFlow(flowId);
  }

  async upsertFlow(
    existingFlowId: string | undefined,
    flowInput: FlowInput | null | undefined,
    errFn?: ErrorCallback
  ): Promise<void> {
    await this.getFlowsModel?.().upsertFlow(existingFlowId, flowInput, errFn);
  }

  reload(): void {
    this.getFlowsModel?.().reload();
  }
}
