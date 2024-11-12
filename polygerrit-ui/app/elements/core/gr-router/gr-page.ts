/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {sameOrigin} from '../../../utils/url-util';

/**
 * This file was originally a copy of https://github.com/visionmedia/page.js.
 * It was converted to TypeScript and stripped off lots of code that we don't
 * need in Gerrit. Thus we reproduce the original LICENSE in js_licenses.txt.
 */

/**
 * This is what registered routes have to provide, see `registerRoute()` and
 * `registerExitRoute()`.
 * `context` provides information about the matched parameters in the URL.
 * Then you can decide to handle the route exclusively (not calling `next()`),
 * or to pass it on to other registered routes. Normally you would not call
 * `next()`, because your regex matching the URL was specific enough.
 */
export type PageCallback = (
  context: PageContext,
  next: PageNextCallback
) => void;

/** See comment on `PageCallback` above. */
export type PageNextCallback = () => void;

/** Options for starting the router. */
export interface PageOptions {
  /**
   * Should the router inspect the current URL and dispatch it when the router
   * is started? Default is `true`, but can be turned off for testing.
   */
  dispatch: boolean;

  /**
   * The base path of the application. For Gerrit this must be set to
   * getBaseUrl().
   */
  base: string;
}

/**
 * The browser `History` API allows `pushState()` to contain an arbitrary state
 * object. Our router only sets `path` on the state and inspects it when
 * handling `popstate` events. This interface is internal only.
 */
interface PageState {
  path?: string;
}

export const UNHANDLED_URL_PATTERNS = [
  /^\/log(in|out)(\/(.+))?$/,
  /^\/plugins\/(.+)$/,
];

const clickEvent = document.ontouchstart ? 'touchstart' : 'click';

export class Page {
  /**
   * When a new URL is dispatched all these routes are called one after another.
   * If a route decides that it wants to handle a URL, then it does not call
   * next().
   */
  private entryRoutes: PageCallback[] = [];

  /**
   * Before a new URL is dispatched exit routes for the previous URL are called.
   * They can clean up some state for example. But they could also prevent the
   * user from navigating away (from within the app), if they don't call next().
   */
  private exitRoutes: PageCallback[] = [];

  /**
   * The path that is currently being dispatched. This is used, so that we can
   * check whether a context is still valid, i.e. ctx.path === currentPath.
   */
  private currentPath = '';

  /**
   * The base path of the application. For Gerrit this must be set to
   * getBaseUrl(). For example https://gerrit.wikimedia.org/ uses r/ as its
   * base path.
   */
  private base = '';

  /**
   * Is set at the beginning of start() and stop(), so that you cannot start
   * the routing twice.
   */
  private running = false;

  /**
   * Keeping around the previous context for being able to call exit routes
   * after creating a new context.
   */
  private prevPageContext?: PageContext;

  /**
   * We don't want to handle popstate events before the document is loaded.
   */
  private documentLoaded = false;

  start(options: PageOptions = {dispatch: true, base: ''}) {
    if (this.running) return;
    this.running = true;
    this.base = options.base;

    window.document.addEventListener(clickEvent, this.clickHandler);
    window.addEventListener('load', this.loadHandler);
    window.addEventListener('popstate', this.popStateHandler);
    if (document.readyState === 'complete') this.documentLoaded = true;

    if (options.dispatch) {
      const loc = window.location;
      this.replace(loc.pathname + loc.search + loc.hash);
    }
  }

  stop() {
    if (!this.running) return;
    this.currentPath = '';
    this.running = false;

    window.document.removeEventListener(clickEvent, this.clickHandler);
    window.removeEventListener('popstate', this.popStateHandler);
    window.removeEventListener('load', this.loadHandler);
  }

  show(path: string, push = true) {
    const ctx = new PageContext(path, {}, this.base);
    const prev = this.prevPageContext;
    this.prevPageContext = ctx;
    this.currentPath = ctx.path;
    this.dispatch(ctx, prev);
    if (push && !ctx.preventPush) ctx.pushState();
  }

  redirect(to: string) {
    setTimeout(() => this.replace(to), 0);
  }

  replace(path: string, state: PageState = {}, dispatch = true) {
    const ctx = new PageContext(path, state, this.base);
    const prev = this.prevPageContext;
    this.prevPageContext = ctx;
    this.currentPath = ctx.path;
    ctx.replaceState(); // replace before dispatching, which may redirect
    if (dispatch) this.dispatch(ctx, prev);
  }

  dispatch(ctx: PageContext, prev?: PageContext) {
    let j = 0;
    const nextExit = () => {
      const fn = this.exitRoutes[j++];
      // First call the exit routes of the previous context. Then proceed
      // to the entry routes for the new context.
      if (!fn) {
        nextEnter();
        return;
      }
      fn(prev!, nextExit);
    };

    let i = 0;
    const nextEnter = () => {
      const fn = this.entryRoutes[i++];

      // Concurrency protection. The context is not valid anymore.
      // Stop calling any further route handlers.
      if (ctx.path !== this.currentPath) {
        ctx.preventPush = true;
        return;
      }

      // You must register a route that handles everything (.*) and does not
      // call next().
      if (!fn) throw new Error('No route has handled the URL.');

      fn(ctx, nextEnter);
    };

    if (prev) {
      nextExit();
    } else {
      nextEnter();
    }
  }

  registerRoute(re: RegExp, fn: PageCallback) {
    this.entryRoutes.push(createRoute(re, fn));
  }

  registerExitRoute(re: RegExp, fn: PageCallback) {
    this.exitRoutes.push(createRoute(re, fn));
  }

  loadHandler = () => {
    setTimeout(() => (this.documentLoaded = true), 0);
  };

  clickHandler = (e: MouseEvent | TouchEvent) => {
    if ((e as MouseEvent).button !== 0) return;
    if (e.metaKey || e.ctrlKey || e.shiftKey) return;
    if (e.defaultPrevented) return;

    let el = e.target as HTMLAnchorElement;
    const eventPath = e.composedPath();
    if (eventPath) {
      for (let i = 0; i < eventPath.length; i++) {
        const pathEl = eventPath[i] as HTMLAnchorElement;
        if (!pathEl.nodeName) continue;
        if (pathEl.nodeName.toUpperCase() !== 'A') continue;
        if (!pathEl.href) continue;

        el = pathEl;
        break;
      }
    }

    while (el && 'A' !== el.nodeName.toUpperCase())
      el = el.parentNode as HTMLAnchorElement;
    if (!el || 'A' !== el.nodeName.toUpperCase()) return;

    if (el.hasAttribute('download') || el.getAttribute('rel') === 'external')
      return;
    const link = el.getAttribute('href');
    if (samePath(el) && (el.hash || '#' === link)) return;
    if (link && link.indexOf('mailto:') > -1) return;
    if (el.target) return;
    if (!sameOrigin(el.href)) return;

    let path = el.pathname + el.search + (el.hash ?? '');
    path = path[0] !== '/' ? '/' + path : path;

    const orig = path;
    if (path.indexOf(this.base) === 0) {
      path = path.substr(this.base.length);
    }
    if (this.base && orig === path && window.location.protocol !== 'file:') {
      return;
    }

    // See issue 40015337: We have to make sure that we only use
    // show()/pushState() for URLs that gr-router will actually handle.
    // Calling pushState() tells the browser that both the previous and the
    // next URL are handled by the same single page application with a
    // popstate event handler. But if we call pushState() and then
    // later `window.location.reload()` from the router and a separate page
    // and document are loaded, then the BACK button will stop working.
    if (UNHANDLED_URL_PATTERNS.find(pattern => pattern.test(path))) {
      return;
    }

    e.preventDefault();
    this.show(orig);
  };

  popStateHandler = (e: PopStateEvent) => {
    if (!this.documentLoaded) return;
    if (e.state) {
      const path = e.state.path;
      this.replace(path, e.state);
    } else {
      const loc = window.location;
      this.show(loc.pathname + loc.search + loc.hash, /* push */ false);
    }
  };
}

function samePath(url: HTMLAnchorElement) {
  const loc = window.location;
  return url.pathname === loc.pathname && url.search === loc.search;
}

function escapeRegExp(s: string) {
  return s.replace(/([.+*?=^!:${}()[\]|/\\])/g, '\\$1');
}

function decodeURIComponentString(val: string | undefined | null) {
  if (!val) return '';
  return decodeURIComponent(val.replace(/\+/g, ' '));
}

export class PageContext {
  /**
   * Includes everything: base, path, query and hash.
   * NOT decoded.
   */
  canonicalPath = '';

  /**
   * Does not include base path.
   * Does not include hash.
   * Includes query string.
   * NOT decoded.
   */
  path = '';

  /** Decoded. Does not include hash. */
  querystring = '';

  /** Decoded. */
  hash = '';

  /**
   * Regular expression matches of capturing groups. The first entry params[0]
   * corresponds to the first capturing group. The entire matched string is not
   * returned in this array.
   * Each param is double decoded.
   */
  params: string[] = [];

  /**
   * Prevents `show()` from eventually calling `pushState()`. For example if
   * the current context is not "valid" anymore, i.e. the URL has changed in the
   * meantime.
   *
   * This is router internal state. Do not use it from routes.
   */
  preventPush = false;

  private title = '';

  constructor(
    path: string,
    private readonly state: PageState = {},
    pageBase = ''
  ) {
    this.title = window.document.title;

    if ('/' === path[0] && 0 !== path.indexOf(pageBase)) path = pageBase + path;
    this.canonicalPath = path;
    const re = new RegExp('^' + escapeRegExp(pageBase));
    this.path = path.replace(re, '') || '/';
    this.state.path = path;

    const i = path.indexOf('?');
    this.querystring =
      i !== -1 ? decodeURIComponentString(path.slice(i + 1)) : '';

    // Does the path include a hash? If yes, then remove it from path and
    // querystring.
    if (this.path.indexOf('#') === -1) return;
    const parts = this.path.split('#');
    this.path = parts[0];
    this.hash = decodeURIComponentString(parts[1]) || '';
    this.querystring = this.querystring.split('#')[0];
  }

  pushState() {
    window.history.pushState(this.state, this.title, this.canonicalPath);
  }

  replaceState() {
    window.history.replaceState(this.state, this.title, this.canonicalPath);
  }

  match(re: RegExp) {
    const qsIndex = this.path.indexOf('?');
    const pathname = qsIndex !== -1 ? this.path.slice(0, qsIndex) : this.path;
    const matches = re.exec(decodeURIComponent(pathname));
    if (matches) {
      this.params = matches
        .slice(1)
        .map(match => decodeURIComponentString(match));
    }
    return !!matches;
  }
}

function createRoute(re: RegExp, fn: Function) {
  return (ctx: PageContext, next: Function) => {
    const matches = ctx.match(re);
    if (matches) {
      fn(ctx, next);
    } else {
      next();
    }
  };
}
