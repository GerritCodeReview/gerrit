/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, Subject} from 'rxjs';
import {
  CheckResult,
  CheckRun,
  ChecksApiConfig,
  ChecksProvider,
} from '../../api/checks';
import {Model} from '../base/model';
import {select} from '../../utils/observable-util';
import {
  CoverageProvider,
  DiffLayerFactory,
  TokenHoverListener,
} from '../../api/annotation';
import {SuggestionsProvider} from '../../api/suggestions';
import {ChangeUpdatesPublisher} from '../../api/change-updates';
import {AiCodeReviewProvider} from '../../api/ai-code-review';
import {FlowsAutosubmitProvider, FlowsProvider} from '../../api/flows';

export interface CoveragePlugin {
  pluginName: string;
  provider: CoverageProvider;
}

export interface ChecksPlugin {
  pluginName: string;
  provider: ChecksProvider;
  config: ChecksApiConfig;
}

export interface ChangeUpdatesPlugin {
  pluginName: string;
  publisher: ChangeUpdatesPublisher;
}

export interface AiCodeReviewPlugin {
  pluginName: string;
  provider: AiCodeReviewProvider;
}

export interface FlowsPlugin {
  pluginName: string;
  provider: FlowsProvider;
}

export interface FlowsAutosubmitPlugin {
  pluginName: string;
  provider: FlowsAutosubmitProvider;
}

export interface SuggestionPlugin {
  pluginName: string;
  provider: SuggestionsProvider;
}

export interface TokenHoverListenerPlugin {
  pluginName: string;
  listener: TokenHoverListener;
}

export interface DiffLayerPlugin {
  pluginName: string;
  factory: DiffLayerFactory;
}

export interface ChecksUpdate {
  pluginName: string;
  run: CheckRun;
  result: CheckResult;
}

/** Application wide state of plugins. */
interface PluginsState {
  /**
   * Initially false. Becomes true, if either all plugins were loaded, or if
   * loading plugins has timed out. Once true, it will not change again.
   */
  pluginsLoaded: boolean;
  /**
   * List of plugins that have called annotationApi().setCoverageProvider().
   */
  coveragePlugins: CoveragePlugin[];
  /**
   * List of plugins that have registered a publisher for change updated events.
   */
  changeUpdatesPlugins: ChangeUpdatesPlugin[];

  /**
   * List of plugins that have called checks().register().
   */
  checksPlugins: ChecksPlugin[];

  /**
   * List of plugins that have called aiCodeReview().register().
   */
  aiCodeReviewPlugins: AiCodeReviewPlugin[];

  /**
   * List of plugins that have called flows().register().
   */
  flowsPlugins: FlowsPlugin[];

  /**
   * List of plugins that have called flows().registerAutosubmitProvider().
   */
  flowsAutosubmitPlugins: FlowsAutosubmitPlugin[];

  /**
   * List of plugins that have called suggestions().register().
   */
  suggestionsPlugins: SuggestionPlugin[];

  /**
   * List of plugins that have called
   * annotationApi().addTokenHoverListener().
   */
  tokenHighlightPlugins: TokenHoverListenerPlugin[];

  /**
   * List of plugins that have registered a diff layer factory.
   */
  diffLayerPlugins: DiffLayerPlugin[];
}

export class PluginsModel extends Model<PluginsState> {
  /** Private version of the event bus below. */
  private checksAnnounceSubject$ = new Subject<ChecksPlugin>();

  /** Event bus for telling the checks models that announce() was called. */
  public checksAnnounce$: Observable<ChecksPlugin> =
    this.checksAnnounceSubject$.asObservable();

  /** Private version of the event bus below. */
  private checksUpdateSubject$ = new Subject<ChecksUpdate>();

  /** Event bus for telling the checks models that updateResult() was called. */
  public checksUpdate$: Observable<ChecksUpdate> =
    this.checksUpdateSubject$.asObservable();

  public checksPlugins$ = select(this.state$, state => state.checksPlugins);

  public coveragePlugins$ = select(this.state$, state => state.coveragePlugins);

  public changeUpdatesPlugins$ = select(
    this.state$,
    state => state.changeUpdatesPlugins
  );

  public aiCodeReviewPlugins$ = select(
    this.state$,
    state => state.aiCodeReviewPlugins
  );

  public flowsPlugins$ = select(this.state$, state => state.flowsPlugins);

  readonly flowsAutosubmitPlugin$ = select(
    this.state$,
    state => state.flowsAutosubmitPlugins
  );

  public suggestionsPlugins$ = select(
    this.state$,
    state => state.suggestionsPlugins
  );

  public diffLayerPlugins$ = select(
    this.state$,
    state => state.diffLayerPlugins
  );

  public pluginsLoaded$ = select(this.state$, state => state.pluginsLoaded);

  constructor() {
    super({
      pluginsLoaded: false,
      coveragePlugins: [],
      changeUpdatesPlugins: [],
      checksPlugins: [],
      aiCodeReviewPlugins: [],
      flowsPlugins: [],
      flowsAutosubmitPlugins: [],
      suggestionsPlugins: [],
      tokenHighlightPlugins: [],
      diffLayerPlugins: [],
    });
  }

  private registerPlugin<K extends keyof Omit<PluginsState, 'pluginsLoaded'>>(
    key: K,
    plugin: PluginsState[K][number],
    typeDescription: string
  ) {
    const list = this.getState()[key] as Array<{pluginName: string}>;
    const alreadyRegistered = list.some(
      p => p.pluginName === plugin.pluginName
    );
    if (alreadyRegistered) {
      console.warn(
        `${plugin.pluginName} tried to register twice as a ${typeDescription}. Ignored.`
      );
      return;
    }
    this.updateState({
      [key]: [...list, plugin],
    });
  }

  coverageRegister(plugin: CoveragePlugin) {
    this.registerPlugin('coveragePlugins', plugin, 'coverage provider');
  }

  getChangeUpdatesPlugins() {
    return this.getState().changeUpdatesPlugins;
  }

  changeUpdatesRegister(plugin: ChangeUpdatesPlugin) {
    this.registerPlugin(
      'changeUpdatesPlugins',
      plugin,
      'change updates provider'
    );
  }

  checksRegister(plugin: ChecksPlugin) {
    this.registerPlugin('checksPlugins', plugin, 'checks provider');
  }

  aiCodeReviewRegister(plugin: AiCodeReviewPlugin) {
    this.registerPlugin(
      'aiCodeReviewPlugins',
      plugin,
      'AI Code Review provider'
    );
  }

  registerFlowsProvider(plugin: FlowsPlugin) {
    this.registerPlugin('flowsPlugins', plugin, 'flows provider');
  }

  registerFlowsAutosubmitProvider(plugin: FlowsAutosubmitPlugin) {
    this.registerPlugin('flowsAutosubmitPlugins', plugin, 'flows provider');
  }

  suggestionsRegister(plugin: SuggestionPlugin) {
    this.registerPlugin('suggestionsPlugins', plugin, 'suggestion provider');
  }

  tokenHoverListenerRegister(plugin: TokenHoverListenerPlugin) {
    this.registerPlugin('tokenHighlightPlugins', plugin, 'hover callback');
  }

  diffLayerRegister(plugin: DiffLayerPlugin) {
    this.registerPlugin('diffLayerPlugins', plugin, 'diff layer provider');
  }

  checksUpdate(update: ChecksUpdate) {
    const plugins = this.getState().checksPlugins;
    const plugin = plugins.find(p => p.pluginName === update.pluginName);
    if (!plugin) {
      console.warn(
        `Plugin '${update.pluginName}' not found. checksUpdate() ignored.`
      );
      return;
    }
    this.checksUpdateSubject$.next(update);
  }

  checksAnnounce(pluginName: string) {
    const plugins = this.getState().checksPlugins;
    const plugin = plugins.find(p => p.pluginName === pluginName);
    if (!plugin) {
      console.warn(
        `Plugin '${pluginName}' not found. checksAnnounce() ignored.`
      );
      return;
    }
    this.checksAnnounceSubject$.next(plugin);
  }
}
