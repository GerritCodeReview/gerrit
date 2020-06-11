import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrRepoHeader} from '../../../../elements/change-list/gr-repo-header/gr-repo-header';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrRepoHeaderCheck extends GrRepoHeader
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `info`);
    }
    {
      const el: HTMLElementTagNameMap['h1'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-1`);
    }
    setTextContent(`${this.repo}`);

    {
      const el: HTMLElementTagNameMap['hr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._repoUrl}`);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (this._webLinks)
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
      }
      {
        const el: HTMLElementTagNameMap['span'] = null!;
        useVars(el);
        el.setAttribute('class', `browse`);
      }
      {
        const el: HTMLElementTagNameMap['dom-repeat'] = null!;
        useVars(el);
      }
      {
        const index = 0;
        const itemsIndexAs = 0;
        useVars(index, itemsIndexAs);
        for(const weblink of this._webLinks!)
        {
          {
            const el: HTMLElementTagNameMap['a'] = null!;
            useVars(el);
            el.setAttribute('href', `${__f(weblink)!.url}`);
          }
          setTextContent(`${__f(weblink)!.name}`);

        }
      }
    }
  }
}

