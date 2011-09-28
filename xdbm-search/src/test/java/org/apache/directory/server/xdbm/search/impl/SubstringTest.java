/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.xdbm.search.impl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmStore;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.tools.StoreUtils;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Tests the SubstringCursor and the SubstringEvaluator.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SubstringTest
{
    private static final Logger LOG = LoggerFactory.getLogger( SubstringTest.class.getSimpleName() );

    File wkdir;
    Store<ServerEntry, Long> store;
    static SchemaManager schemaManager = null;


    @BeforeClass
    public static void setup() throws Exception
    {
        String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = SubstringTest.class.getResource( "" ).getPath();
            int targetPos = path.indexOf( "target" );
            workingDirectory = path.substring( 0, targetPos + 6 );
        }

        File schemaRepository = new File( workingDirectory, "schema" );
        SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( new File( workingDirectory ) );
        extractor.extractOrCopy( true );
        LdifSchemaLoader loader = new LdifSchemaLoader( schemaRepository );
        schemaManager = new DefaultSchemaManager( loader );

        boolean loaded = schemaManager.loadAllEnabled();

        if ( !loaded )
        {
            fail( "Schema load failed : " + ExceptionUtils.printErrors( schemaManager.getErrors() ) );
        }

        loaded = schemaManager.loadWithDeps( loader.getSchema( "collective" ) );

        if ( !loaded )
        {
            fail( "Schema load failed : " + ExceptionUtils.printErrors( schemaManager.getErrors() ) );
        }
    }


    @Before
    public void createStore() throws Exception
    {
        destryStore();

        // setup the working directory for the store
        wkdir = File.createTempFile( getClass().getSimpleName(), "db" );
        wkdir.delete();
        wkdir = new File( wkdir.getParentFile(), getClass().getSimpleName() );
        wkdir.mkdirs();

        // initialize the store
        store = new JdbmStore<ServerEntry>();
        store.setName( "example" );
        store.setCacheSize( 10 );
        store.setWorkingDirectory( wkdir );
        store.setSyncOnWrite( false );

        store.addIndex( new JdbmIndex( SchemaConstants.OU_AT_OID ) );
        store.addIndex( new JdbmIndex( SchemaConstants.CN_AT_OID ) );
        StoreUtils.loadExampleData( store, schemaManager );
        LOG.debug( "Created new store" );
    }


    @After
    public void destryStore() throws Exception
    {
        if ( store != null )
        {
            store.destroy();
        }

        store = null;
        if ( wkdir != null )
        {
            FileUtils.deleteDirectory( wkdir );
        }

        wkdir = null;
    }


    @Test
    public void testIndexedCnStartsWithJ() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.CN_AT_OID, "j", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        assertEquals( node, evaluator.getExpression() );
        assertTrue( cursor.isElementReused() );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 8, ( long ) cursor.get().getId() );
        assertEquals( "jack daniels", cursor.get().getValue() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 11, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        assertFalse( cursor.isClosed() );
        cursor.close();
        assertTrue( cursor.isClosed() );

        // ---------- test first ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.first();

        assertTrue( cursor.available() );
        assertEquals( 8, ( long ) cursor.get().getId() );
        assertEquals( "jack daniels", cursor.get().getValue() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 11, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test afterLast ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.afterLast();
        assertFalse( cursor.available() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 11, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 8, ( long ) cursor.get().getId() );
        assertEquals( "jack daniels", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );

        // ---------- test last ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.last();
        assertTrue( cursor.available() );

        assertEquals( 11, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "johnny walker", cursor.get().getValue() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 8, ( long ) cursor.get().getId() );
        assertEquals( "jack daniels", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );
    }


    @Test
    public void testIndexedCnStartsWithJim() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.CN_AT_OID, "jim", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        assertEquals( node, evaluator.getExpression() );
        assertTrue( cursor.isElementReused() );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test first ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.first();

        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test afterLast ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.afterLast();
        assertFalse( cursor.available() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );

        // ---------- test last ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.last();
        assertTrue( cursor.available() );

        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );
    }


    @Test
    public void testIndexedCnEndsWithBean() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.CN_AT_OID, null, "bean" );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        assertEquals( node, evaluator.getExpression() );
        assertTrue( cursor.isElementReused() );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test first ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.first();

        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test afterLast ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.afterLast();
        assertFalse( cursor.available() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );

        // ---------- test last ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.last();
        assertTrue( cursor.available() );

        assertEquals( 10, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 9, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );
        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "jim bean", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );
    }


    @Test
    public void testNonIndexedSnStartsWithB() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.SN_AT_OID, "b", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        assertEquals( node, evaluator.getExpression() );
        assertTrue( cursor.isElementReused() );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "bean", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );
        cursor.close();

        // ---------- test first ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.first();

        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "bean", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test afterLast ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.afterLast();
        assertFalse( cursor.available() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "bean", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );

        // ---------- test last ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.last();
        assertTrue( cursor.available() );

        assertTrue( cursor.available() );
        assertEquals( 6, ( long ) cursor.get().getId() );
        assertEquals( "bean", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );
    }


    @Test
    public void testIndexedSnEndsWithEr() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.SN_AT_OID, null, "er" );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        assertEquals( node, evaluator.getExpression() );
        assertTrue( cursor.isElementReused() );

        cursor.beforeFirst();

        assertTrue( cursor.next() );
        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "walker", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test first ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.first();

        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "walker", cursor.get().getValue() );

        assertFalse( cursor.next() );
        assertFalse( cursor.available() );

        // ---------- test afterLast ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.afterLast();
        assertFalse( cursor.available() );

        assertTrue( cursor.previous() );
        assertTrue( cursor.available() );
        assertEquals( 5, ( long ) cursor.get().getId() );
        assertEquals( "walker", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );

        // ---------- test last ----------

        cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.last();
        assertTrue( cursor.available() );

        assertTrue( cursor.available() );
        assertEquals( 5L, ( long ) cursor.get().getId() );
        assertEquals( "walker", cursor.get().getValue() );

        assertFalse( cursor.previous() );
        assertFalse( cursor.available() );
    }


    @Test
    public void testNonIndexedAttributes() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.SN_AT_OID, "walk", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        ForwardIndexEntry<String, ServerEntry, Long> indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 5L );
        assertTrue( evaluator.evaluate( indexEntry ) );
        indexEntry.setId( 3L );
        indexEntry.setObject( null );
        assertFalse( evaluator.evaluate( indexEntry ) );
        indexEntry.setId( 6L );
        indexEntry.setObject( null );
        assertFalse( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.SN_AT_OID, "wa", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 5L );
        indexEntry.setObject( store.lookup( 5L ) );
        assertTrue( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.SEARCHGUIDE_AT_OID, "j", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        indexEntry.setObject( store.lookup( 6L ) );
        assertFalse( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.ST_AT_OID, "j", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        indexEntry.setObject( store.lookup( 6L ) );
        assertFalse( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.NAME_AT_OID, "j", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        indexEntry.setObject( store.lookup( 6L ) );
        assertTrue( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.NAME_AT_OID, "s", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        indexEntry.setObject( store.lookup( 6L ) );
        assertTrue( evaluator.evaluate( indexEntry ) );
    }


    @Test
    public void testEvaluatorIndexed() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.CN_AT_OID, "jim", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        ForwardIndexEntry<String, ServerEntry, Long> indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        assertTrue( evaluator.evaluate( indexEntry ) );
        indexEntry.setId( 3L );
        indexEntry.setObject( null );
        assertFalse( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.CN_AT_OID, "j", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        indexEntry.setObject( store.lookup( 6L ) );
        assertTrue( evaluator.evaluate( indexEntry ) );

        node = new SubstringNode( SchemaConstants.CN_AT_OID, "s", null );
        evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        indexEntry = new ForwardIndexEntry<String, ServerEntry, Long>();
        indexEntry.setId( 6L );
        indexEntry.setObject( store.lookup( 6L ) );
        assertFalse( evaluator.evaluate( indexEntry ) );
    }


    @Test
    public void testEvaluatorNotIndexed() throws Exception
    {
    }


    @Test(expected = InvalidCursorPositionException.class)
    public void testInvalidCursorPositionException() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.SN_AT_OID, "b", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.get();
    }


    @Test(expected = InvalidCursorPositionException.class)
    public void testInvalidCursorPositionException2() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.CN_AT_OID, "j", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );
        cursor.get();
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportBeforeWithoutIndex() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.SN_AT_OID, "j", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        // test before()
        ForwardIndexEntry<String, ServerEntry, Long> entry = new ForwardIndexEntry<String, ServerEntry, Long>();
        entry.setValue( SchemaConstants.SN_AT_OID );
        cursor.before( entry );
    }


    @Test(expected = UnsupportedOperationException.class)
    public void testUnsupportAfterWithoutIndex() throws Exception
    {
        SubstringNode node = new SubstringNode( SchemaConstants.SN_AT_OID, "j", null );
        SubstringEvaluator<Long> evaluator = new SubstringEvaluator<Long>( node, store, schemaManager );
        SubstringCursor<Long> cursor = new SubstringCursor<Long>( store, evaluator );

        // test before()
        ForwardIndexEntry<String, ServerEntry, Long> entry = new ForwardIndexEntry<String, ServerEntry, Long>();
        entry.setValue( SchemaConstants.SN_AT_OID );
        cursor.after( entry );
    }
}
