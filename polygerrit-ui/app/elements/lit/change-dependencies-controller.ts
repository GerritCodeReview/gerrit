/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {ReactiveController, ReactiveControllerHost} from 'lit';
import {
  ChangeDependenciesRequest,
  ChangeDependencyChange,
  ChangeDependencyEdge,
} from '../../api/change-dependencies';
import {ChangeInfo, ChangeStatus} from '../../api/rest-api';
import {getAppContext} from '../../services/app-context';
import {ChangeDependenciesPlugin} from '../../models/plugins/plugins-model';
import {resolve} from '../../models/dependency';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {subscribe} from './subscription-controller';

/** One batched request per provider and view; native rendering never waits for it. */
export class ChangeDependenciesController implements ReactiveController {
  private resultEdges: ChangeDependencyEdge[] = [];

  private resultChanges: ChangeInfo[] = [];

  get additionalChanges(): ChangeInfo[] {
    return this.enabled() && this.changes() === this.previousChanges
      ? this.resultChanges
      : [];
  }

  get edges(): ChangeDependencyEdge[] {
    // Never render a result from the previous source array, even for one frame.
    return this.enabled() && this.changes() === this.previousChanges
      ? this.resultEdges
      : [];
  }

  private plugins: ChangeDependenciesPlugin[] = [];

  private generation = 0;

  private previousChanges?: ChangeDependencyChange[];

  private previousEnabled?: boolean;

  private previousGeneration = -1;

  private abort?: AbortController;

  private timeout?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly host: ReactiveControllerHost & HTMLElement,
    private readonly context: ChangeDependenciesRequest['context'],
    private readonly changes: () => ChangeDependencyChange[],
    private readonly enabled: () => boolean
  ) {
    const getPluginLoader = resolve(host, pluginLoaderToken);
    subscribe(
      host,
      () => getPluginLoader().pluginsModel.changeDependenciesPlugins$,
      plugins => {
        this.plugins = plugins;
        this.generation++;
        this.host.requestUpdate();
      }
    );
    host.addController(this);
  }

  hostUpdated() {
    const changes = this.changes();
    const enabled = this.enabled();
    // Reference equality intentionally refetches on reloads of unchanged revisions.
    // Hosts keep the input array stable until actual source data changes.
    if (
      changes === this.previousChanges &&
      enabled === this.previousEnabled &&
      this.generation === this.previousGeneration
    )
      return;
    this.previousChanges = changes;
    this.previousEnabled = enabled;
    this.previousGeneration = this.generation;
    this.abort?.abort();
    clearTimeout(this.timeout);
    const controller = new AbortController();
    this.abort = controller;
    if (this.resultEdges.length || this.resultChanges.length) {
      this.resultEdges = [];
      this.resultChanges = [];
      this.host.requestUpdate();
    }
    if (!enabled || !changes.length || !this.plugins.length) return;
    const snapshot = Object.freeze(
      changes.map(change => Object.freeze({...change}))
    );
    const allowed = new Set(snapshot.map(change => change._number));
    const results = this.plugins.map(() => {
      return {
        edges: [] as ChangeDependencyEdge[],
        changes: [] as ChangeInfo[],
      };
    });
    const request: ChangeDependenciesRequest = {
      context: this.context,
      changes: snapshot,
      signal: controller.signal,
    };
    this.timeout = setTimeout(() => controller.abort(), 30000);
    const tasks = this.plugins.map((plugin, index) =>
      Promise.resolve()
        .then(() =>
          controller.signal.aborted
            ? {edges: []}
            : plugin.provider.fetch(request)
        )
        .then(async response => {
          if (controller.signal.aborted || this.abort !== controller) return;
          const nominations =
            this.context === 'change-list'
              ? new Map(
                  (response.changes ?? [])
                    .slice(0, 500)
                    .filter(
                      change =>
                        Number.isSafeInteger(change._number) &&
                        change._number > 0 &&
                        !allowed.has(change._number)
                    )
                    .map(change => [change._number, change])
                )
              : new Map<number, ChangeDependencyChange>();
          const loaded: ChangeInfo[] = [];
          const ids = [...nominations.keys()];
          for (let start = 0; start < ids.length; start += 50) {
            if (controller.signal.aborted || this.abort !== controller) return;
            const batch = ids.slice(start, start + 50);
            const changes = await getAppContext()
              .restApiService.getChanges(
                batch.length,
                batch.map(id => `change:${id}`).join(' OR ')
              )
              .catch(error => {
                if (!controller.signal.aborted)
                  console.warn('Could not load dependency rows', error);
                return [];
              });
            for (const change of changes ?? []) {
              const expected = nominations.get(change._number);
              const revision =
                change.indexed_dependencies?.revision ??
                change.current_revision;
              const patchSet =
                change.indexed_dependencies?.patch_set_number ??
                change.current_revision_number;
              if (
                !expected ||
                change.status === ChangeStatus.MERGED ||
                change.project !== expected.project ||
                change.branch !== expected.branch ||
                (expected.current_revision &&
                  revision &&
                  expected.current_revision !== revision) ||
                (expected.current_revision_number &&
                  patchSet &&
                  expected.current_revision_number !== patchSet)
              )
                continue;
              loaded.push(change);
            }
          }
          if (controller.signal.aborted || this.abort !== controller) return;
          const endpoints = new Set([
            ...allowed,
            ...loaded.map(change => change._number),
          ]);
          results[index].changes = loaded;
          results[index].edges = (response.edges ?? [])
            .filter(
              edge =>
                endpoints.has(edge.from) &&
                endpoints.has(edge.to) &&
                (edge.type === 'git' || edge.type === 'depends-on')
            )
            .map(({from, to, type}) => {
              return {from, to, type};
            });
          this.resultEdges = [
            ...new Map(
              results
                .flatMap(result => result.edges)
                .map(edge => [`${edge.from}:${edge.to}:${edge.type}`, edge])
            ).values(),
          ];
          this.resultChanges = [
            ...new Map(
              results
                .flatMap(result => result.changes)
                .map(change => [change._number, change])
            ).values(),
          ];
          this.host.requestUpdate();
        })
        .catch(error => {
          if (!controller.signal.aborted)
            console.warn(
              `Dependency provider ${plugin.pluginName} failed`,
              error
            );
        })
    );
    Promise.allSettled(tasks).then(() => {
      if (this.abort === controller) clearTimeout(this.timeout);
    });
  }

  hostDisconnected() {
    this.abort?.abort();
    clearTimeout(this.timeout);
    this.previousChanges = undefined;
    this.resultEdges = [];
    this.resultChanges = [];
  }
}
