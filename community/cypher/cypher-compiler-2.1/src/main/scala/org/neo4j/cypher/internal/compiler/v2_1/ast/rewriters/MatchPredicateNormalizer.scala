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
package org.neo4j.cypher.internal.compiler.v2_1.ast.rewriters

import org.neo4j.cypher.internal.compiler.v2_1.ast._
import org.neo4j.cypher.internal.helpers.PartialFunctionSupport
import org.neo4j.cypher.internal.compiler.v2_1.ast.NodePattern
import org.neo4j.cypher.internal.compiler.v2_1.ast.RelationshipPattern
import org.neo4j.cypher.internal.compiler.v2_1.ast.Identifier
import org.neo4j.cypher.internal.compiler.v2_1.ast.MapExpression
import org.neo4j.cypher.internal.compiler.v2_1.ast.Property

trait MatchPredicateNormalizer {
  val extract: PartialFunction[AnyRef, Vector[Expression]]
  val replace: PartialFunction[AnyRef, AnyRef]
}

case class MatchPredicateNormalizerChain(normalizers: MatchPredicateNormalizer*) extends MatchPredicateNormalizer {
  val extract = PartialFunctionSupport.reduceAnyDefined(normalizers.map(_.extract))(Vector.empty[Expression])(_ ++ _)
  val replace = PartialFunctionSupport.composeIfDefined(normalizers.map(_.replace))
}

object PropertyPredicateNormalizer extends MatchPredicateNormalizer {
  override val extract: PartialFunction[AnyRef, Vector[Expression]] = {
    case NodePattern(Some(id), _, Some(props), _)               => propertyPredicates(id, props)
    case RelationshipPattern(Some(id), _, _, _, Some(props), _) => propertyPredicates(id, props)
  }

  override val replace: PartialFunction[AnyRef, AnyRef] = {
    case p@NodePattern(Some(_) ,_, Some(_), _)               => p.copy(properties = None)(p.position)
    case p@RelationshipPattern(Some(_), _, _, _, Some(_), _) => p.copy(properties = None)(p.position)
  }

  private def propertyPredicates(id: Identifier, props: Expression): Vector[Expression] = props match {
    case mapProps: MapExpression =>
      mapProps.items.map {
        case (propId, expression) => Equals(Property(id, propId)(mapProps.position), expression)(mapProps.position)
      }.toVector
    case expr: Expression =>
      Vector(Equals(id, expr)(expr.position))
    case _ =>
      Vector.empty
  }
}

object LabelPredicateNormalizer extends MatchPredicateNormalizer {
  override val extract: PartialFunction[AnyRef, Vector[Expression]] = {
    case p@NodePattern(Some(id), labels, _, _) if !labels.isEmpty => Vector(HasLabels(id, labels)(p.position))
  }

  override val replace: PartialFunction[AnyRef, AnyRef] = {
    case p@NodePattern(Some(id), labels, _, _) if !labels.isEmpty => p.copy(labels = Seq.empty)(p.position)
  }
}

