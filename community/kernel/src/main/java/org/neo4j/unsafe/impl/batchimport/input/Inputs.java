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
package org.neo4j.unsafe.impl.batchimport.input;

import java.io.File;

import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.unsafe.impl.batchimport.cache.idmapping.IdMapping;
import org.neo4j.unsafe.impl.batchimport.input.csv.Configuration;
import org.neo4j.unsafe.impl.batchimport.input.csv.CsvInput;
import org.neo4j.unsafe.impl.batchimport.input.csv.DataFactories;
import org.neo4j.unsafe.impl.batchimport.input.csv.DataFactory;
import org.neo4j.unsafe.impl.batchimport.input.csv.IdType;

import static org.neo4j.helpers.collection.Iterables.asResourceIterable;
import static org.neo4j.helpers.collection.Iterables.iterable;

public class Inputs
{
    public static Input input( final Iterable<InputNode> nodes, final Iterable<InputRelationship> relationships,
            final IdMapping idMapping )
    {
        final ResourceIterable<InputNode> resourceNodes = asResourceIterable( nodes );
        final ResourceIterable<InputRelationship> resourceRelationships = asResourceIterable( relationships );
        return new Input()
        {
            @Override
            public ResourceIterable<InputRelationship> relationships()
            {
                return resourceRelationships;
            }

            @Override
            public ResourceIterable<InputNode> nodes()
            {
                return resourceNodes;
            }

            @Override
            public IdMapping idMapping()
            {
                return idMapping;
            }
        };
    }

    public static Input csv( File nodes, File relationships, IdType idType,
            Configuration configuration )
    {
        Iterable<DataFactory> nodeData = iterable( DataFactories.data( nodes ) );
        Iterable<DataFactory> relationshipData = iterable( DataFactories.data( relationships ) );
        return new CsvInput(
                nodeData, DataFactories.defaultFormatNodeFileHeader(),
                relationshipData, DataFactories.defaultFormatRelationshipFileHeader(),
                idType, configuration );
    }
}
