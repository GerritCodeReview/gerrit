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
import {CoverageProvider, TokenHoverListener} from '../../api/annotation';
import {SuggestionsProvider} from '../../api/suggestions';

export interface CoveragePlugin {
  pluginName: string;
  provider: CoverageProvider;
}

export interface ChecksPlugin {
  pluginName: string;
  provider: ChecksProvider;
  config: ChecksApiConfig;
}

export interface SuggestionPlugin {
  pluginName: string;
  provider: SuggestionsProvider;
}

export interface TokenHoverListenerPlugin {
  pluginName: string;
  listener: TokenHoverListener;
}

export interface ChecksUpdate {
  pluginName: string;
  run: CheckRun;
  result: CheckResult;
}

/** Application wide state of plugins. */
interface PluginsState {
  /**
   * List of plugins that have called annotationApi().setCoverageProvider().
   */
  coveragePlugins: CoveragePlugin[];
  /**
   * List of plugins that have called checks().register().
   */
  checksPlugins: ChecksPlugin[];

  /**
   * List of plugins that have called suggestions().register().
   */
  suggestionsPlugins: SuggestionPlugin[];

  /**
   * List of plugins that have called
   * annotationApi().addTokenHoverListener().
   */
  tokenHighlightPlugins: TokenHoverListenerPlugin[];
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

  constructor() {
    super({
      coveragePlugins: [],
      checksPlugins: [],
      suggestionsPlugins: [],
      tokenHighlightPlugins: [],
    });
  }

  coverageRegister(plugin: CoveragePlugin) {
    const nextState = {...this.getState()};
    nextState.coveragePlugins = [...nextState.coveragePlugins];
    const alreadyRegistered = nextState.coveragePlugins.some(
      p => p.pluginName === plugin.pluginName
    );
    if (alreadyRegistered) {
      console.warn(
        `${plugin.pluginName} tried to register twice as a coverage provider. Ignored.`
      );
      return;
    }
    nextState.coveragePlugins.push(plugin);
    this.setState(nextState);
  }

  checksRegister(plugin: ChecksPlugin) {
    const nextState = {...this.getState()};
    nextState.checksPlugins = [...nextState.checksPlugins];
    const alreadyRegistered = nextState.checksPlugins.some(
      p => p.pluginName === plugin.pluginName
    );
    if (alreadyRegistered) {
      console.warn(
        `${plugin.pluginName} tried to register twice as a checks provider. Ignored.`
      );
      return;
    }
    nextState.checksPlugins.push(plugin);
    this.setState(nextState);
  }

  suggestionsRegister(plugin: SuggestionPlugin) {
    const nextState = {...this.getState()};
    nextState.suggestionsPlugins = [...nextState.suggestionsPlugins];
    const alreadyRegistered = nextState.suggestionsPlugins.some(
      p => p.pluginName === plugin.pluginName
    );
    if (alreadyRegistered) {
      console.warn(
        `${plugin.pluginName} tried to register twice as a suggestion provider. Ignored.`
      );
      return;
    }
    nextState.suggestionsPlugins.push(plugin);
    this.setState(nextState);
  }

  tokenHoverListenerRegister(plugin: TokenHoverListenerPlugin) {
    const nextState = {...this.getState()};
    nextState.tokenHighlightPlugins = [...nextState.tokenHighlightPlugins];
    const alreadyRegistered = nextState.tokenHighlightPlugins.some(
      p => p.pluginName === plugin.pluginName
    );
    if (alreadyRegistered) {
      console.warn(
        `${plugin.pluginName} tried to register twice as a hover callback. Ignored.`
      );
      return;
    }
    nextState.tokenHighlightPlugins.push(plugin);
    this.setState(nextState);
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
