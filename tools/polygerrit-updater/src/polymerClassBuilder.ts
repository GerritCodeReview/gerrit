import {ClassElement, Expression, GetAccessorDeclaration, HeritageClause, Identifier, MethodDeclaration, Statement} from 'typescript';
import {LegacyLifecycleMethodName} from '../../src/polymerComponentParser';
import * as ts from 'typescript';
import * as tsUtils from '../../src/tsUtils';

export enum PolymerClassMemberType {
  IsAccessor,
  Constructor,
  PolymerPropertiesAccessor,
  PolymerObserversAccessor,
  Method,
  LifecycleMethod,
  GetAccessor,
  Other
}

interface PolymerClassMember {
  member: ClassElement;
  memberType: PolymerClassMemberType;
  //originalPos === -1 for a completely new member
  // For converted members, this is a position in the original .js file
  originalPos?: number;
}

export class PolymerClassBuilder {
  private readonly members: PolymerClassMember[] = [];
  public readonly constructorStatements: Statement[] = [];
  private baseType: ts.ExpressionWithTypeArguments | undefined;
  public constructor() {
  }

  public addIsAccessor(accessor: GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.IsAccessor
    });
  }

  public addPolymerPropertiesAccessor(originalPos: number, accessor: GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.PolymerPropertiesAccessor,
      originalPos: originalPos
    });
  }

  public addPolymerObserversAccessor(originalPos: number, accessor: GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.PolymerObserversAccessor,
      originalPos: originalPos
    });
  }


  public addClassFieldInitializer(name: string | Identifier, initializer: Expression) {
    const assignment = ts.createAssignment(ts.createPropertyAccess(ts.createThis(), name), initializer);
    this.constructorStatements.push(tsUtils.addNewLineAfterNode(ts.createExpressionStatement(assignment)));
  }
  public addConstructor(constructor: MethodDeclaration) {
    this.members.push({
      member: constructor,
      memberType: PolymerClassMemberType.Constructor
    });
  }

  public addMethod(originalPos: number, method: MethodDeclaration) {
    this.members.push({
      member: method,
      memberType: PolymerClassMemberType.Method,
      originalPos: originalPos
    });
  }

  public addGetAccessor(originalPos: number, accessor: GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.GetAccessor,
      originalPos: originalPos
    });
  }

  public addLifecycleMethod(name: LegacyLifecycleMethodName, originalPos: number | undefined, method: MethodDeclaration) {
    this.members.push({
      member: method,
      memberType: PolymerClassMemberType.LifecycleMethod,
      originalPos: originalPos
    })
  }


  public setBaseType(type: ts.ExpressionWithTypeArguments) {
    if(this.baseType) {
      throw new Error("Class can have only one base type");
    }
    this.baseType = type;
  }

  public build(): ts.ClassDeclaration {
    let heritageClauses: HeritageClause[] = [];
    if(this.baseType) {
      const extendClause = ts.createHeritageClause(ts.SyntaxKind.ExtendsKeyword, [this.baseType]);
      heritageClauses.push(extendClause);
    }
    this.addConstructor(createConstructor(this.constructorStatements));
    //Sort members
    return ts.createClassDeclaration(undefined, undefined, this.className, undefined, heritageClauses, members.map(m => m.member))

  }
}