/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from '../../../api/plugin';
import {StylePluginApi} from '../../../api/styles';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {ServerInfo} from '../../../types/common';
import {
  computeThemeStyleCacheKey,
  getThemeStyleCacheStorageKey,
} from '../../../utils/url-util';

export interface HeaderStyleSnapshot {
  headerBg: string;
  headerHeight: string;
  headerBorderBottom: string;
  headerBoxShadow: string;
}

type ThemeClassName = 'lightTheme' | 'darkTheme';

interface StoredThemeStyleCache {
  key: string;
  rules: string[];
  hasCustomHeaderTitle: boolean;
  hasCustomMobileHeaderTitle?: boolean;
  snapshots?: Partial<Record<ThemeClassName, HeaderStyleSnapshot>>;
}

const recordedRules = new Set<string>();

export function resetRecordedCSSRules() {
  recordedRules.clear();
}

export function getRecordedCSSRules(): string[] {
  return [...recordedRules];
}

function getCurrentThemeClass(): ThemeClassName {
  return document.documentElement.classList.contains('darkTheme')
    ? 'darkTheme'
    : 'lightTheme';
}

export function measureHeaderSnapshot(): HeaderStyleSnapshot | undefined {
  const appEl = document.querySelector('gr-app');
  const appElem = appEl?.shadowRoot?.querySelector('gr-app-element');
  const header =
    appElem?.shadowRoot?.querySelector<HTMLElement>('gr-main-header');
  if (!header) return undefined;
  const height = Math.round(header.getBoundingClientRect().height);
  if (height <= 0) return undefined;
  const headerStyle = getComputedStyle(header);
  const headerBg =
    headerStyle.backgroundImage && headerStyle.backgroundImage !== 'none'
      ? headerStyle.background
      : headerStyle.backgroundColor;
  const nav = header.shadowRoot?.querySelector<HTMLElement>('nav.hideOnMobile');
  const navStyle = nav ? getComputedStyle(nav) : undefined;
  return {
    headerBg,
    headerHeight: `${height}px`,
    headerBorderBottom: navStyle?.borderBottom || '',
    headerBoxShadow: navStyle?.boxShadow || '',
  };
}

function writeThemeStyleCache(
  serverInfo: ServerInfo,
  hasCustomHeaderTitle: boolean,
  hasCustomMobileHeaderTitle: boolean
) {
  try {
    const storageKey = getThemeStyleCacheStorageKey();
    const key = computeThemeStyleCacheKey(serverInfo);
    let existingSnapshots: Partial<
      Record<ThemeClassName, HeaderStyleSnapshot>
    > = {};
    const rawExisting = window.localStorage?.getItem(storageKey);
    if (rawExisting) {
      try {
        const parsed = JSON.parse(rawExisting) as StoredThemeStyleCache;
        if (
          parsed &&
          parsed.key === key &&
          parsed.snapshots &&
          typeof parsed.snapshots === 'object'
        ) {
          existingSnapshots = {...parsed.snapshots};
        }
      } catch {
        // Ignore corrupt existing JSON.
      }
    }
    const currentSnapshot = measureHeaderSnapshot();
    if (currentSnapshot) {
      existingSnapshots[getCurrentThemeClass()] = currentSnapshot;
    }
    const payload: StoredThemeStyleCache = {
      key,
      rules: [...recordedRules],
      hasCustomHeaderTitle,
      hasCustomMobileHeaderTitle,
      snapshots: existingSnapshots,
    };
    window.localStorage?.setItem(storageKey, JSON.stringify(payload));
  } catch {
    // Ignore storage quota or private-mode access errors.
  }
}

export function updateThemeStyleCacheSnapshot() {
  try {
    const storageKey = getThemeStyleCacheStorageKey();
    const rawExisting = window.localStorage?.getItem(storageKey);
    if (!rawExisting) return;
    const parsed = JSON.parse(rawExisting) as StoredThemeStyleCache;
    if (!parsed || typeof parsed !== 'object' || !parsed.key) return;
    const currentSnapshot = measureHeaderSnapshot();
    if (!currentSnapshot) return;
    parsed.snapshots = {
      ...(parsed.snapshots && typeof parsed.snapshots === 'object'
        ? parsed.snapshots
        : {}),
      [getCurrentThemeClass()]: currentSnapshot,
    };
    window.localStorage?.setItem(storageKey, JSON.stringify(parsed));
  } catch {
    // Ignore storage quota or private-mode access errors.
  }
}

export function reconcilePluginStyleCache(
  serverInfo: ServerInfo | undefined,
  hasCustomHeaderTitle: boolean,
  canPersist: boolean,
  hasCustomMobileHeaderTitle = false
) {
  document.head.querySelector('style#plugin-style-cache')?.remove();
  const root = document.documentElement;
  if (!hasCustomHeaderTitle) {
    root.classList.remove('gr-custom-header-title');
  }
  if (!hasCustomMobileHeaderTitle) {
    root.classList.remove('gr-custom-mobile-header-title');
  }
  root.classList.remove('gr-header-shell-ready');
  root.style.removeProperty('--gr-cached-header-bg');
  root.style.removeProperty('--gr-cached-header-height');
  root.style.removeProperty('--gr-cached-header-border-bottom');
  root.style.removeProperty('--gr-cached-header-box-shadow');
  if (hasCustomHeaderTitle || hasCustomMobileHeaderTitle) {
    window.requestAnimationFrame?.(() => {
      root.classList.remove(
        'gr-custom-header-title',
        'gr-custom-mobile-header-title'
      );
    });
  }
  if (!canPersist || !serverInfo) return;
  if (
    !serverInfo.default_theme &&
    !serverInfo.plugin?.js_resource_paths?.length
  ) {
    return;
  }
  writeThemeStyleCache(
    serverInfo,
    hasCustomHeaderTitle,
    hasCustomMobileHeaderTitle
  );
  window.requestAnimationFrame?.(() => {
    updateThemeStyleCacheSnapshot();
  });
}

function getOrCreatePluginStyleEl(): HTMLStyleElement {
  const el =
    document.head.querySelector<HTMLStyleElement>('style#plugin-style');
  if (el) return el;

  const styleEl = document.createElement('style');
  styleEl.setAttribute('id', 'plugin-style');
  // Append at the end so that they override the default light and dark theme
  // styles.
  document.head.appendChild(styleEl);
  return styleEl;
}

export class GrPluginStyleApi implements StylePluginApi {
  constructor(
    private readonly reporting: ReportingService,
    private readonly plugin: PluginApi
  ) {
    this.reporting.trackApi(this.plugin, 'style', 'constructor');
  }

  insertCSSRule(rule: string): void {
    this.reporting.trackApi(this.plugin, 'style', 'insertCSSRule');

    const styleEl = getOrCreatePluginStyleEl();
    try {
      styleEl.sheet?.insertRule(rule, styleEl.sheet.cssRules.length);
      recordedRules.add(rule);
    } catch (error) {
      console.error(
        `Failed to insert CSS rule for plugin ${this.plugin.getPluginName()}: ${error}`
      );
    }
  }
}
