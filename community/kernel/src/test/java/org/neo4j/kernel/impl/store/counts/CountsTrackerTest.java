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
package org.neo4j.kernel.impl.store.counts;

import static org.neo4j.kernel.impl.store.CommonAbstractStore.buildTypeDescriptorAndVersion;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.neo4j.helpers.Function;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.api.CountsKey;
import org.neo4j.kernel.impl.store.CountsOracle;
import org.neo4j.kernel.impl.store.kvstore.SortedKeyValueStore;
import org.neo4j.register.Register;
import org.neo4j.register.Registers;
import org.neo4j.test.Barrier;
import org.neo4j.test.EphemeralFileSystemRule;
import org.neo4j.test.PageCacheRule;
import org.neo4j.test.ThreadingRule;

public class CountsTrackerTest
{
    @Test
    public void shouldStoreCounts() throws Exception
    {
        // given
        CountsTracker.createEmptyCountsStore( pageCache(), storeFile(), VERSION );
        CountsOracle oracle = oracle();

        // when
        try ( CountsTracker tracker = new CountsTracker( fs.get(), pageCache(), storeFile() ) )
        {
            oracle.update( tracker );
            tracker.rotate( 1 );
        }

        // then
        try ( CountsTracker tracker = new CountsTracker( fs.get(), pageCache(), storeFile() ) )
        {
            oracle.verify( tracker );
        }
    }

    @Test
    public void shouldUpdateCountsOnExistingStore() throws Exception
    {
        // given
        CountsTracker.createEmptyCountsStore( pageCache(), storeFile(), VERSION );
        CountsOracle oracle = oracle();
        try ( CountsTracker tracker = new CountsTracker( fs.get(), pageCache(), storeFile() ) )
        {
            oracle.update( tracker );
            tracker.rotate( 1 );

            oracle.verify( tracker );

            // when
            CountsOracle delta = new CountsOracle();
            {
                CountsOracle.Node n1 = delta.node( 1 );
                CountsOracle.Node n2 = delta.node( 1, 4 );  // Label 4 has not been used before...
                delta.relationship( n1, 1, n2 );
                delta.relationship( n2, 2, n1 ); // relationshipType 2 has not been used before...
            }
            delta.update( tracker );
            delta.update( oracle );

            // then
            oracle.verify( tracker );

            // when
            tracker.rotate( 2 );
        }

        // then
        try ( CountsTracker tracker = new CountsTracker( fs.get(), pageCache(), storeFile() ) )
        {
            oracle.verify( tracker );
        }
    }

    @Test
    public void shouldBeAbleToReadUpToDateValueWhileAnotherThreadIsPerformingRotation() throws Exception
    {
        // given
        CountsTracker.createEmptyCountsStore( pageCache(), storeFile(), VERSION );
        CountsOracle oracle = oracle();
        try ( CountsTracker tracker = new CountsTracker( fs.get(), pageCache(), storeFile() ) )
        {
            oracle.update( tracker );
            tracker.rotate( 1 );
        }

        // when
        final CountsOracle delta = new CountsOracle();
        {
            CountsOracle.Node n1 = delta.node( 1 );
            CountsOracle.Node n2 = delta.node( 1, 4 );  // Label 4 has not been used before...
            delta.relationship( n1, 1, n2 );
            delta.relationship( n2, 2, n1 ); // relationshipType 2 has not been used before...
        }
        delta.update( oracle );

        Barrier.Control barrier = new Barrier.Control();
        try ( CountsTracker tracker = new InstrumentedCountsTracker( fs.get(), pageCache(), storeFile(), barrier ) )
        {
            Future<Void> task = threading.execute( new Function<CountsTracker, Void>()
            {
                @Override
                public Void apply( CountsTracker tracker )
                {
                    try
                    {
                        delta.update( tracker );
                        tracker.rotate( 2 );
                    }
                    catch ( IOException e )
                    {
                        throw new AssertionError( e );
                    }
                    return null;
                }
            }, tracker );

            // then
            barrier.await();
            oracle.verify( tracker );
            barrier.release();
            task.get();
            oracle.verify( tracker );
        }
    }

    private static final String VERSION = buildTypeDescriptorAndVersion( CountsTracker.STORE_DESCRIPTOR );
    public final @Rule EphemeralFileSystemRule fs = new EphemeralFileSystemRule();
    public final @Rule TestName testName = new TestName();
    public final @Rule PageCacheRule pageCache = new PageCacheRule();
    public final @Rule ThreadingRule threading = new ThreadingRule();
    private final Config config = new Config();

    public CountsOracle oracle()
    {
        CountsOracle oracle = new CountsOracle();
        CountsOracle.Node n0 = oracle.node( 0, 1 );
        CountsOracle.Node n1 = oracle.node( 0, 3 );
        CountsOracle.Node n2 = oracle.node( 2, 3 );
        CountsOracle.Node n3 = oracle.node( 2 );
        oracle.relationship( n0, 1, n2 );
        oracle.relationship( n1, 1, n3 );
        oracle.relationship( n1, 1, n2 );
        oracle.relationship( n0, 1, n3 );
        return oracle;
    }

    private File storeFile()
    {
        return new File( testName.getMethodName() );
    }

    private PageCache pageCache()
    {
        return pageCache.getPageCache( fs.get(), config );
    }

    private static class InstrumentedCountsTracker extends CountsTracker
    {
        private final Barrier barrier;

        InstrumentedCountsTracker( FileSystemAbstraction fs, PageCache pageCache, File storeFileBase,
                                   Barrier barrier )
        {
            super( fs, pageCache, storeFileBase );
            this.barrier = barrier;
        }

        @Override
        CountsStore.Writer<CountsKey, Register.LongRegister> nextWriter( State state, long lastCommittedTxId )
                throws IOException
        {
            final CountsStoreWriter writer = (CountsStoreWriter) super.nextWriter( state, lastCommittedTxId );
            return new CountsStore.Writer<CountsKey, Register.LongRegister>()
            {
                private final Register.LongRegister valueRegister = Registers.newLongRegister();

                @Override
                public SortedKeyValueStore<CountsKey, Register.LongRegister> openForReading() throws IOException
                {
                    barrier.reached();
                    return writer.openForReading();
                }

                @Override
                public void close() throws IOException
                {
                    writer.close();
                }

                @Override
                public void visit( CountsKey key )
                {
                    writer.valueRegister().write( valueRegister.read() );
                    writer.visit( key );
                }

                @Override
                public Register.LongRegister valueRegister()
                {
                    return valueRegister;
                }
            };
        }
    }
}
