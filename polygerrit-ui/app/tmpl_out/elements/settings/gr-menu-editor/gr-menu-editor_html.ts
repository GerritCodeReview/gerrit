import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrMenuEditor} from '../../../../elements/settings/gr-menu-editor/gr-menu-editor';

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

export class GrMenuEditorCheck extends GrMenuEditor
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['thead'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `nameHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `url-header`);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this.menuItems!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        setTextContent(`${__f(item)!.name}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `urlCell`);
        }
        setTextContent(`${__f(item)!.url}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `buttonColumn`);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.setAttribute('dataIndex', `${index}`);
          el.addEventListener('click', e => this._handleMoveUpButton.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('class', `moveUpButton`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `buttonColumn`);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.setAttribute('dataIndex', `${index}`);
          el.addEventListener('click', e => this._handleMoveDownButton.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('class', `moveDownButton`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.setAttribute('dataIndex', `${index}`);
          el.addEventListener('click', e => this._handleDeleteButton.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('class', `remove-button`);
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['tfoot'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.addEventListener('keydown', this._handleInputKeydown.bind(this));
      el.bindValue = this._newName;
      this._newName = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.addEventListener('keydown', this._handleInputKeydown.bind(this));
      el.bindValue = this._newName;
      this._newName = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.setAttribute('class', `newUrlInput`);
      el.addEventListener('keydown', this._handleInputKeydown.bind(this));
      el.bindValue = this._newUrl;
      this._newUrl = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.setAttribute('class', `newUrlInput`);
      el.addEventListener('keydown', this._handleInputKeydown.bind(this));
      el.bindValue = this._newUrl;
      this._newUrl = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.setAttribute('disabled', `${this._computeAddDisabled(this._newName, this._newUrl)}`);
      el.addEventListener('click', this._handleAddButton.bind(this));
    }
  }
}

