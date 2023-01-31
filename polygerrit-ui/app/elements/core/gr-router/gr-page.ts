/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export type PageNextCallback = () => void;

export type PageCallback = (
  context: PageContext,
  next: PageNextCallback
) => void;

export interface PageOptions {
  popstate?: boolean;
  dispatch?: boolean;
}

interface PageState {
  path?: string;
}

function decodeURIComponentString(val: string | undefined | null) {
  if (!val) return '';
  return decodeURIComponent(val.replace(/\+/g, ' '));
}

const clickEvent = document.ontouchstart ? 'touchstart' : 'click';

export class Page {
  private callbacks: PageCallback[] = [];

  private exits: PageCallback[] = [];

  private current = '';

  private _base = '';

  private _running = false;

  private prevPageContext?: PageContext;

  private loaded = false;

  len = 0;

  base(path: string) {
    if (0 === arguments.length) return this._base;
    this._base = path;
    return this._base;
  }

  _getBase() {
    return this._base;
  }

  start(options: PageOptions) {
    if (this._running) return;
    if (options.popstate !== false) {
      window.addEventListener('popstate', this._onpopstate, false);
    }
    window.document.addEventListener(clickEvent, this.clickHandler, false);

    if (document.readyState === 'complete') {
      this.loaded = true;
    } else {
      window.addEventListener(
        'load',
        () => {
          setTimeout(() => (this.loaded = true), 0);
        },
        {once: true}
      );
    }

    if (false === options.dispatch) return;

    this._running = true;
    const url =
      window.location.pathname + window.location.search + window.location.hash;
    this.replace(url);
  }

  stop() {
    if (!this._running) return;
    this.current = '';
    this.len = 0;
    this._running = false;

    window.document.removeEventListener(clickEvent, this.clickHandler, false);
    window.removeEventListener('popstate', this._onpopstate, false);
  }

  show(
    path: string,
    state: PageState = {},
    dispatch?: boolean,
    push?: boolean
  ) {
    const ctx = new PageContext(path, state, this);
    const prev = this.prevPageContext;
    this.prevPageContext = ctx;
    this.current = ctx.path;
    if (false !== dispatch) this.dispatch(ctx, prev);
    if (false !== ctx.handled && false !== push) ctx.pushState();
    return ctx;
  }

  back() {
    if (this.len > 0) {
      window.history.back();
      this.len--;
    } else {
      setTimeout(() => this.show(this._getBase()));
    }
  }

  redirect(to: string) {
    setTimeout(() => this.replace(to), 0);
  }

  replace(
    path: string,
    state: PageState = {},
    _?: boolean,
    dispatch?: boolean
  ) {
    const ctx = new PageContext(path, state, this);
    const prev = this.prevPageContext;
    this.prevPageContext = ctx;
    this.current = ctx.path;
    ctx.save(); // save before dispatching, which may redirect
    if (false !== dispatch) this.dispatch(ctx, prev);
    return ctx;
  }

  dispatch(ctx: PageContext, prev?: PageContext) {
    let i = 0;
    let j = 0;

    const nextExit = () => {
      const fn = this.exits[j++];
      if (!fn) {
        nextEnter();
        return;
      }
      fn(prev!, nextExit);
    };

    const nextEnter = () => {
      const fn = this.callbacks[i++];

      if (ctx.path !== this.current) {
        ctx.handled = false;
        return;
      }
      if (!fn) {
        this.unhandled(ctx);
        return;
      }
      fn(ctx, nextEnter);
    };

    if (prev) {
      nextExit();
    } else {
      nextEnter();
    }
  }

  registerRoute(re: RegExp, fn: PageCallback) {
    this.callbacks.push(middleware(re, fn));
  }

  exit(re: RegExp, fn: PageCallback) {
    this.exits.push(middleware(re, fn));
  }

  clickHandler = (e: MouseEvent | TouchEvent) => {
    if ((e as MouseEvent).button !== 0) return;
    if (e.metaKey || e.ctrlKey || e.shiftKey) return;
    if (e.defaultPrevented) return;

    // ensure link
    // use shadow dom when available if not, fall back to composedPath()
    // for browsers that only have shady
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
    if (this._samePath(el) && (el.hash || '#' === link)) return;
    if (link && link.indexOf('mailto:') > -1) return;
    if (el.target) return;
    if (!this.sameOrigin(el.href)) return;

    let path = el.pathname + el.search + (el.hash || '');
    path = path[0] !== '/' ? '/' + path : path;

    const orig = path;
    const pageBase = this._getBase();
    if (path.indexOf(pageBase) === 0) {
      path = path.substr(pageBase.length);
    }
    if (pageBase && orig === path && window.location.protocol !== 'file:') {
      return;
    }
    e.preventDefault();
    this.show(orig);
  };

  _onpopstate = () => (e: PopStateEvent) => {
    if (!this.loaded) return;
    if (e.state) {
      const path = e.state.path;
      this.replace(path, e.state);
    } else {
      const loc = window.location;
      this.show(
        loc.pathname + loc.search + loc.hash,
        undefined,
        undefined,
        false
      );
    }
  };

  _toURL(href: string) {
    if (typeof URL === 'function') {
      return new URL(href, window.location.toString());
    } else {
      const anc = window.document.createElement('a');
      anc.href = href;
      return anc;
    }
  }

  sameOrigin(href: string) {
    if (!href) return false;
    const url = this._toURL(href);
    const loc = window.location;
    return (
      loc.protocol === url.protocol &&
      loc.hostname === url.hostname &&
      loc.port === url.port
    );
  }

  _samePath(url: HTMLAnchorElement) {
    const loc = window.location;
    return url.pathname === loc.pathname && url.search === loc.search;
  }

  /**
   * Unhandled `ctx`. When it's not the initial
   * popstate then redirect. If you wish to handle
   * 404s on your own use `page('*', callback)`.
   *
   * @param ctx
   * @api private
   */
  unhandled(ctx: PageContext) {
    if (ctx.handled) return;
    const current = window.location.pathname + window.location.search;
    if (current === ctx.canonicalPath) return;
    this.stop();
    ctx.handled = false;
    window.location.href = ctx.canonicalPath;
  }
}

function escapeRegExp(s: string) {
  return s.replace(/([.+*?=^!:${}()[\]|/\\])/g, '\\$1');
}

export class PageContext {
  canonicalPath = '';

  path = '';

  title = '';

  state: PageState = {};

  querystring = '';

  params: Record<string, string> = {};

  pathname = '';

  hash = '';

  handled = false;

  constructor(path: string, state: PageState, private readonly _page: Page) {
    const pageBase = _page._getBase();
    if ('/' === path[0] && 0 !== path.indexOf(pageBase)) path = pageBase + path;
    const i = path.indexOf('?');

    this.canonicalPath = path;
    const re = new RegExp('^' + escapeRegExp(pageBase));
    this.path = path.replace(re, '') || '/';
    this.title = window.document.title;
    this.state = state;
    this.state.path = path;
    this.querystring = ~i ? decodeURIComponentString(path.slice(i + 1)) : '';
    this.pathname = decodeURIComponentString(~i ? path.slice(0, i) : path);
    this.params = {};

    // fragment
    if (!~this.path.indexOf('#')) return;
    const parts = this.path.split('#');
    this.path = this.pathname = parts[0];
    this.hash = decodeURIComponentString(parts[1]) || '';
    this.querystring = this.querystring.split('#')[0];
  }

  pushState() {
    this._page.len++;
    window.history.pushState(this.state, this.title, this.canonicalPath);
  }

  save() {
    window.history.replaceState(this.state, this.title, this.canonicalPath);
  }
}

function middleware(re: RegExp, fn: Function) {
  return (ctx: PageContext, next: Function) => {
    const qsIndex = ctx.path.indexOf('?');
    const pathname = ~qsIndex ? ctx.path.slice(0, qsIndex) : ctx.path;
    const m = re.exec(decodeURIComponent(pathname));
    if (m) {
      delete ctx.params[0];
      for (let i = 1; i < m.length; ++i) {
        ctx.params[`${i - 1}`] = decodeURIComponentString(m[i]);
      }
      fn(ctx, next);
    } else {
      next();
    }
  };
}
