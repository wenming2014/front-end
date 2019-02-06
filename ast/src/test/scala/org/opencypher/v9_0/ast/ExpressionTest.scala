/*
 * Copyright © 2002-2018 Neo4j Sweden AB (http://neo4j.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.v9_0.ast

import org.opencypher.v9_0.expressions._
import org.opencypher.v9_0.util.IdentityMap
import org.opencypher.v9_0.util.test_helpers.CypherFunSuite

class ExpressionTest extends CypherFunSuite with AstConstructionTestSupport {

  test("should compute dependencies of simple expressions") {
    varFor("a").dependencies should equal(Set(varFor("a")))
    literalInt(1).dependencies should equal(Set())
  }

  test("should compute dependencies of composite expressions") {
    add(varFor("a"), subtract(literalInt(1), varFor("b"))).dependencies should equal(Set(varFor("a"), varFor("b")))
  }

  test("should compute dependencies for filtering expressions") {
    // extract(x IN (n)-->(k) | head(nodes(x)) )
    val pat: RelationshipsPattern = RelationshipsPattern(
      RelationshipChain(
        NodePattern(Some(varFor("n")), Seq.empty, None)_,
        RelationshipPattern(None, Seq.empty, None, None, SemanticDirection.OUTGOING)_,
        NodePattern(Some(varFor("k")), Seq.empty, None)_
      )_
    )_
    val expr: Expression = ExtractExpression(
      varFor("x"),
      PatternExpression(pat),
      None,
      Some(function("head", function("nodes", varFor("x"))))
    )_

    expr.dependencies should equal(Set(varFor("n"), varFor("k")))
  }

  test("should compute dependencies for nested filtering expressions") {
    // extract(x IN (n)-->(k) | extract(y IN [1,2,3] | y) )
    val pat: RelationshipsPattern = RelationshipsPattern(
      RelationshipChain(
        NodePattern(Some(varFor("n")), Seq.empty, None)_,
        RelationshipPattern(None, Seq.empty, None, None, SemanticDirection.OUTGOING)_,
        NodePattern(Some(varFor("k")), Seq.empty, None)_
      )_
    )_
    val innerExpr: Expression = ExtractExpression(
      varFor("y"),
      listOf(literalInt(1), literalInt(2), literalInt(3)),
      None,
      Some(varFor("y"))
    )_
    val expr: Expression = ExtractExpression(
      varFor("x"),
      PatternExpression(pat),
      None,
      Some(innerExpr)
    )_

    expr.dependencies should equal(Set(varFor("n"), varFor("k")))
  }

  test("should compute dependencies for pattern comprehensions") {
    // [ (n)-->(k) | k ]
    val pat: RelationshipsPattern = RelationshipsPattern(
      RelationshipChain(
        NodePattern(Some(varFor("n")), Seq.empty, None)_,
        RelationshipPattern(None, Seq.empty, None, None, SemanticDirection.OUTGOING)_,
        NodePattern(Some(varFor("k")), Seq.empty, None)_
      )_
    )_
    val expr = PatternComprehension(
      namedPath = None,
      pattern = pat,
      predicate = None,
      projection = varFor("k")
    )(pos, Set.empty)

    expr.withOuterScope(Set(varFor("n"), varFor("k"))).dependencies should equal(Set(varFor("n"), varFor("k")))
    expr.withOuterScope(Set.empty).dependencies should equal(Set.empty)
  }

  test("should compute inputs of composite expressions") {
    val identA = varFor("a")
    val identB = varFor("b")
    val lit1 = literalInt(1)
    val subExpr = subtract(lit1, identB)
    val addExpr = add(identA, subExpr)

    IdentityMap(addExpr.inputs: _*) should equal(IdentityMap(
      identA -> Set.empty,
      identB -> Set.empty,
      lit1 -> Set.empty,
      subExpr -> Set.empty,
      addExpr -> Set.empty
    ))
  }

  test("should compute inputs for filtering expressions") {
    // given
    val pat = PatternExpression(RelationshipsPattern(
      RelationshipChain(
        NodePattern(Some(varFor("n")), Seq.empty, None)_,
        RelationshipPattern(None, Seq.empty, None, None, SemanticDirection.OUTGOING)_,
        NodePattern(Some(varFor("k")), Seq.empty, None)_
      )_
    )_)

    val callNodes: Expression = function("nodes", varFor("x"))
    val callHead: Expression = function("head", callNodes)

    // extract(x IN (n)-->(k) | head(nodes(x)) )
    val expr: Expression = ExtractExpression(
      varFor("x"),
      pat,
      None,
      Some(callHead)
    )_

    // when
    val inputs = IdentityMap(expr.inputs: _*)

    // then
    inputs(callNodes) should equal(Set(varFor("x")))
    inputs(callHead) should equal(Set(varFor("x")))
    inputs(expr) should equal(Set.empty)
    inputs(pat) should equal(Set.empty)
  }
}
