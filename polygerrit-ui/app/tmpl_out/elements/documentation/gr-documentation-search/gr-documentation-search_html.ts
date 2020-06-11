import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrDocumentationSearch} from '../../../../elements/documentation/gr-documentation-search/gr-documentation-search';

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

export class GrDocumentationSearchCheck extends GrDocumentationSearch
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['gr-list-view'] = null!;
      useVars(el);
      el.filter = this._filter;
      el.offset = 0;
      el.loading = this._loading;
      el.path = `/Documentation`;
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
      el.setAttribute('id', `list`);
      el.setAttribute('class', `genericList`);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
      el.setAttribute('class', `headerRow`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `name topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `name topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `name topHeader`);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
      el.setAttribute('id', `loading`);
      el.setAttribute('class', `loadingMsg ${this.computeLoadingClass(this._loading)}`);
    }
    {
      const el: HTMLElementTagNameMap['td'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
      el.setAttribute('class', `${this.computeLoadingClass(this._loading)}`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._documentationSearches!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
          el.setAttribute('class', `table`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `name`);
        }
        {
          const el: HTMLElementTagNameMap['a'] = null!;
          useVars(el);
          el.setAttribute('href', `${this._computeSearchUrl(__f(item)!.url)}`);
        }
        setTextContent(`${__f(item)!.title}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
      }
    }
  }
}

