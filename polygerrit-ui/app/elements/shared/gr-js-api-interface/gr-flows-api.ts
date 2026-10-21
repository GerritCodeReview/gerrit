/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {FlowsModel} from '../../../models/flows/flows-model';
import {
  FlowsAutosubmitProvider,
  FlowsPluginApi,
  FlowsProvider,
  FlowsStateInfo,
} from '../../../api/flows';
import {ChangeInfo, FlowInfo, FlowInput} from '../../../api/rest-api';
import {ErrorCallback} from '../../../api/rest';
import {getChangePrefix} from '../../../utils/url-util';
import {throwingErrorCallback} from '../gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
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

  getState(): FlowsStateInfo | undefined {
    const state = this.getFlowsModel?.().getState();
    if (!state) return undefined;
    return {
      isEnabled: state.isEnabled,
      flows: state.flows,
      loading: state.loading,
      errorMessage: state.errorMessage,
    };
  }

  onStateChanged(callback: (state: FlowsStateInfo) => void): () => void {
    const model = this.getFlowsModel?.();
    if (!model) return () => {};
    const sub = model.state$.subscribe(state => {
      callback({
        isEnabled: state.isEnabled,
        flows: state.flows,
        loading: state.loading,
        errorMessage: state.errorMessage,
      });
    });
    return () => sub.unsubscribe();
  }

  async createFlow(
    flowInput: FlowInput,
    errFn: ErrorCallback = throwingErrorCallback,
    reload = true
  ): Promise<FlowInfo | undefined> {
    const model = this.getFlowsModel?.();
    if (!model) {
      throw new Error('FlowsModel is not available');
    }
    return await model.createFlow(flowInput, errFn, reload);
  }

  async deleteFlow(
    flowId: string,
    errFn: ErrorCallback = throwingErrorCallback,
    reload = true
  ): Promise<void> {
    const model = this.getFlowsModel?.();
    if (!model) {
      throw new Error('FlowsModel is not available');
    }
    await model.deleteFlow(flowId, errFn, reload);
  }

  reload(): void {
    this.getFlowsModel?.().reload();
  }

  getChangePrefix(change?: Pick<ChangeInfo, 'project' | '_number'>): string {
    return (
      this.getFlowsModel?.().getChangePrefix(change) ?? getChangePrefix(change)
    );
  }
}
