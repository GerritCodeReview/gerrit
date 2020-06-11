import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrSearchBar} from '../../../../elements/core/gr-search-bar/gr-search-bar';

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

export class GrSearchBarCheck extends GrSearchBar
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['form'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
      useVars(el);
      el.label = this.label;
      el.showSearchIcon = true;
      el.setAttribute('id', `searchInput`);
      el.text = this._inputVal;
      this._inputVal = el.text;
      el.query = this.query;
      el.addEventListener('commit', this._handleInputCommit.bind(this));
      el.allowNonSuggestedValues = true;
      el.multi = true;
      el.threshold = this._threshold;
      el.tabComplete = true;
      el.verticalOffset = 30;
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._computeHelpDocLink(this.docBaseUrl)}`);
      el.setAttribute('class', `help`);
    }
    {
      const el: HTMLElementTagNameMap['iron-icon'] = null!;
      useVars(el);
    }
  }
}

