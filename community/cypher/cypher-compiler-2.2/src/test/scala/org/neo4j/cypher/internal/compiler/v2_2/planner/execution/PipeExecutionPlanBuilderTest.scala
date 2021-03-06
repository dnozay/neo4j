/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v2_2.planner.execution

import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_2.LabelId
import org.neo4j.cypher.internal.compiler.v2_2.ast.convert.commands.ExpressionConverters._
import org.neo4j.cypher.internal.compiler.v2_2.ast.{Collection, RelTypeName, SignedDecimalIntegerLiteral, SignedIntegerLiteral}
import org.neo4j.cypher.internal.compiler.v2_2.commands.{expressions => legacy}
import org.neo4j.cypher.internal.compiler.v2_2.executionplan.PipeInfo
import org.neo4j.cypher.internal.compiler.v2_2.pipes.{EntityByIdExprs => PipeEntityByIdExprs, _}
import org.neo4j.cypher.internal.compiler.v2_2.planner._
import org.neo4j.cypher.internal.compiler.v2_2.planner.logical.plans.{EntityByIdExprs => PlanEntityByIdExprs, _}
import org.neo4j.graphdb.Direction

class PipeExecutionPlanBuilderTest extends CypherFunSuite with LogicalPlanningTestSupport {

  implicit val planContext = newMockedPlanContext
  implicit val pipeMonitor = monitors.newMonitor[PipeMonitor]()
  implicit val LogicalPlanningContext = newMockedLogicalPlanningContext(planContext)
  implicit val pipeBuildContext = newMockedPipeExecutionPlanBuilderContext
  val patternRel = PatternRelationship("r", ("a", "b"), Direction.OUTGOING, Seq.empty, SimplePatternLength)

  val planBuilder = new PipeExecutionPlanBuilder(monitors)

  def build(f: PlannerQuery => LogicalPlan): PipeInfo =
    planBuilder.build(f(solved))

  test("projection only query") {
    val logicalPlan = Projection(
      SingleRow(Set.empty)(solved)(), Map("42" -> SignedDecimalIntegerLiteral("42") _))_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(ProjectionNewPipe(NullPipe(), Map("42" -> legacy.Literal(42)))())
  }

  test("simple pattern query") {
    val logicalPlan = AllNodesScan(IdName("n"), Set.empty)_
    val pipeInfo: PipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(AllNodesScanPipe("n")())
  }

  test("simple label scan query") {
    val logicalPlan = NodeByLabelScan(IdName("n"), Right(LabelId(12)), Set.empty)_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(NodeByLabelScanPipe("n", Right(LabelId(12)))())
  }

  test("simple node by id seek query") {
    val astLiteral: SignedIntegerLiteral = SignedDecimalIntegerLiteral("42")_
    val logicalPlan = NodeByIdSeek(IdName("n"), PlanEntityByIdExprs(Seq(astLiteral)), Set.empty)_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(NodeByIdSeekPipe("n", PipeEntityByIdExprs(Seq(astLiteral.asCommandExpression)))())
  }

  test("simple node by id seek query with multiple values") {
    val astCollection: Collection = Collection(
      Seq(SignedDecimalIntegerLiteral("42")_, SignedDecimalIntegerLiteral("43")_, SignedDecimalIntegerLiteral("43")_)
    )_
    val logicalPlan = NodeByIdSeek(IdName("n"), PlanEntityByIdExprs(Seq(astCollection)), Set.empty)_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(NodeByIdSeekPipe("n", PipeEntityByIdExprs(Seq(astCollection.asCommandExpression)))())
  }

  test("simple relationship by id seek query") {
    val astLiteral: SignedIntegerLiteral = SignedDecimalIntegerLiteral("42")_
    val fromNode = "from"
    val toNode = "to"
    val logicalPlan = DirectedRelationshipByIdSeek(IdName("r"), PlanEntityByIdExprs(Seq(astLiteral)), IdName(fromNode), IdName(toNode), Set.empty)_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(DirectedRelationshipByIdSeekPipe("r", PipeEntityByIdExprs(Seq(astLiteral.asCommandExpression)), toNode, fromNode)())
  }

  test("simple relationship by id seek query with multiple values") {
    val astCollection: Seq[SignedIntegerLiteral] =
      Seq(SignedDecimalIntegerLiteral("42")_, SignedDecimalIntegerLiteral("43")_, SignedDecimalIntegerLiteral("43")_)

    val fromNode = "from"
    val toNode = "to"
    val logicalPlan = DirectedRelationshipByIdSeek(IdName("r"), PlanEntityByIdExprs(astCollection), IdName(fromNode), IdName(toNode), Set.empty)_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(DirectedRelationshipByIdSeekPipe("r", PipeEntityByIdExprs(astCollection.map(_.asCommandExpression)), toNode, fromNode)())
  }

  test("simple undirected relationship by id seek query with multiple values") {
    val astCollection: Seq[SignedIntegerLiteral] =
      Seq(SignedDecimalIntegerLiteral("42")_, SignedDecimalIntegerLiteral("43")_, SignedDecimalIntegerLiteral("43")_)

    val fromNode = "from"
    val toNode = "to"
    val logicalPlan = UndirectedRelationshipByIdSeek(IdName("r"), PlanEntityByIdExprs(astCollection), IdName(fromNode), IdName(toNode), Set.empty)_
    val pipeInfo = build(logicalPlan)

    pipeInfo should not be 'updating
    pipeInfo.periodicCommit should equal(None)
    pipeInfo.pipe should equal(UndirectedRelationshipByIdSeekPipe("r", PipeEntityByIdExprs(astCollection.map(_.asCommandExpression)), toNode, fromNode)())
  }

  test("simple cartesian product") {
    val lhs = AllNodesScan(IdName("n"), Set.empty)(solved)
    val rhs = AllNodesScan(IdName("m"), Set.empty)(solved)
    val logicalPlan = CartesianProduct(lhs, rhs)_
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe should equal(CartesianProductPipe(AllNodesScanPipe("n")(), AllNodesScanPipe("m")())())
  }

  test("simple expand") {
    val logicalPlan = Expand(AllNodesScan("a", Set.empty)(solved), "a", Direction.INCOMING, Direction.INCOMING, Seq(), "b", "r1", SimplePatternLength)_
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe should equal(ExpandPipeForIntTypes( AllNodesScanPipe("a")(), "a", "r1", "b", Direction.INCOMING, Seq() )())
  }

  test("simple hash join") {
    val logicalPlan =
      NodeHashJoin(
        Set(IdName("b")),
        Expand(AllNodesScan("a", Set.empty)(solved), "a", Direction.INCOMING, Direction.INCOMING, Seq(), "b", "r1", SimplePatternLength)(solved),
        Expand(AllNodesScan("c", Set.empty)(solved), "c", Direction.INCOMING, Direction.INCOMING, Seq(), "b", "r2", SimplePatternLength)(solved)
      )_
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe should equal(NodeHashJoinPipe(
      Set("b"),
      ExpandPipeForIntTypes( AllNodesScanPipe("a")(), "a", "r1", "b", Direction.INCOMING, Seq() )(),
      ExpandPipeForIntTypes( AllNodesScanPipe("c")(), "c", "r2", "b", Direction.INCOMING, Seq() )()
    )())
  }

  test("use ExpandPipeForStringTypes when at least one is unknown") {
    val names = Seq("existing1", "nonexisting", "existing3")
    val relTypeNames = names.map(new RelTypeName(_)(null))
    val logicalPlan = Expand(AllNodesScan("a", Set.empty)(solved), "a", Direction.INCOMING, Direction.INCOMING, relTypeNames, "b", "r1", SimplePatternLength)_
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe should equal(ExpandPipeForStringTypes( AllNodesScanPipe("a")(), "a", "r1", "b", Direction.INCOMING, names)())
  }

  test("use ExpandPipeForIntTypes when all tokens are known") {
    val names = Seq("existing1", "existing2", "existing3")
    val relTypeNames = names.map(new RelTypeName(_)(null))
    val logicalPlan = Expand(AllNodesScan("a", Set.empty)(solved), "a", Direction.INCOMING, Direction.INCOMING, relTypeNames, "b", "r1", SimplePatternLength)_
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe should equal(ExpandPipeForIntTypes( AllNodesScanPipe("a")(), "a", "r1", "b", Direction.INCOMING, Seq(1, 2, 3))())
  }

  test("use VarExpandPipeForStringTypes when at least one is unknown") {
    val names = Seq("existing1", "nonexisting", "existing3")
    val relTypeNames = names.map(new RelTypeName(_)(null))
    val logicalPlan = Expand(AllNodesScan("a", Set.empty)(solved), "a", Direction.INCOMING, Direction.INCOMING, relTypeNames, "b", "r1", new VarPatternLength(2, Some(5))) _
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe match {
      case pipe: VarLengthExpandPipeForStringTypes => pipe.copy(filteringStep = null)(pipe.estimatedCardinality) should equal(
        VarLengthExpandPipeForStringTypes(AllNodesScanPipe("a")(), "a", "r1", "b", Direction.INCOMING, Direction.INCOMING, names, 2, Some(5))().copy(filteringStep = null)(pipe.estimatedCardinality))

      case _ => fail("expected VarLengthExpandPipeForStringTypes")
    }
  }

  test("use VarExpandPipeForIntTypes when all tokens are known") {
    val names = Seq("existing1", "existing2", "existing3")
    val relTypeNames = names.map(new RelTypeName(_)(null))
    val logicalPlan = Expand(AllNodesScan("a", Set.empty)(solved), "a", Direction.INCOMING, Direction.INCOMING, relTypeNames, "b", "r1", new VarPatternLength(2, Some(5))) _
    val pipeInfo = build(logicalPlan)

    pipeInfo.pipe match {
      case pipe: VarLengthExpandPipeForIntTypes => pipe.copy(filteringStep = null)(pipe.estimatedCardinality) should equal(
        VarLengthExpandPipeForIntTypes(AllNodesScanPipe("a")(), "a", "r1", "b", Direction.INCOMING, Direction.INCOMING, Seq(1, 2, 3), 2, Some(5))().copy(filteringStep = null)(pipe.estimatedCardinality))

      case _ => fail("expected VarLengthExpandPipeForIntTypes")
    }
  }
}
