/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.partition.impl.btree.jdbm;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import jdbm.RecordManager;
import jdbm.recman.BaseRecordManager;

import org.apache.directory.server.xdbm.Table;
import org.apache.directory.server.xdbm.Tuple;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.comparators.SerializableComparator;
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
 * Tests the Cursor functionality of a JdbmTable when duplicate keys are not
 * supported.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class NoDupsCursorTest
{
    private static final Logger LOG = LoggerFactory.getLogger( NoDupsCursorTest.class.getSimpleName() );
    private static final String TEST_OUTPUT_PATH = "test.output.path";

    transient Table<String,String> table;
    transient File dbFile;
    transient RecordManager recman;
    private static SchemaManager schemaManager;


    @BeforeClass
    public static void init() throws Exception
    {
        String workingDirectory = System.getProperty( "workingDirectory" );

        if ( workingDirectory == null )
        {
            String path = DupsContainerCursorTest.class.getResource( "" ).getPath();
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
    }


    @Before
    public void createTable() throws Exception
    {
        File tmpDir = null;
        
        if ( System.getProperty( TEST_OUTPUT_PATH, null ) != null )
        {
            tmpDir = new File( System.getProperty( TEST_OUTPUT_PATH ) );
        }

        dbFile = File.createTempFile( getClass().getSimpleName(), "db", tmpDir );
        recman = new BaseRecordManager( dbFile.getAbsolutePath() );

        SerializableComparator<String> comparator = new SerializableComparator<String>( SchemaConstants.INTEGER_ORDERING_MATCH_MR_OID );
        comparator.setSchemaManager( schemaManager );

        table = new JdbmTable<String,String>( schemaManager, "test", recman, 
            comparator, null, null );
        LOG.debug( "Created new table and populated it with data" );
    }


    @After
    public void destryTable() throws Exception
    {
        table.close();
        table = null;
        recman.close();
        recman = null;
        dbFile.deleteOnExit();
        String fileToDelete = dbFile.getAbsolutePath();
        new File( fileToDelete + ".db" ).delete();
        new File( fileToDelete + ".lg" ).delete();
        dbFile = null;
    }


    @Test( expected=InvalidCursorPositionException.class )
    public void testEmptyTable() throws Exception
    {
        Cursor<Tuple<String,String>> cursor = table.cursor();
        assertNotNull( cursor );
        
        assertFalse( cursor.available() );
        assertFalse( cursor.isClosed() );
        assertTrue( cursor.isElementReused() );

        cursor = table.cursor();
        assertFalse( cursor.previous() );

        cursor = table.cursor();
        assertFalse( cursor.next() );

        cursor.after( new Tuple<String,String>( "7", "7" ) );
        cursor.get();
    }


    @Test
    public void testOnTableWithSingleEntry() throws Exception
    {
        table.put( "1", "1" );
        Cursor<Tuple<String,String>> cursor = table.cursor();
        assertTrue( cursor.first() );
    
        Tuple<String,String> tuple = cursor.get();
        assertEquals( "1", tuple.getKey() );
        assertEquals( "1", tuple.getValue() );
    
        cursor.beforeFirst();
        assertFalse( cursor.previous() );
        assertTrue( cursor.next() );
    }

    
    @Test
    public void testOnTableWithMultipleEntries() throws Exception
    {
        for( int i=1; i < 10; i++ )
        {
            String istr = Integer.toString( i );
            table.put( istr, istr );
        }
    
        Cursor<Tuple<String,String>> cursor = table.cursor();
        
        cursor.after( new Tuple<String,String>( "2", "2" ) );
        assertTrue( cursor.next() );
    
        Tuple<String,String> tuple = cursor.get();
        assertEquals( "3", tuple.getKey() );
        assertEquals( "3", tuple.getValue() );
    
        cursor.before( new Tuple<String,String>( "7", "7" ) );
        cursor.next();
        tuple = cursor.get();
        assertEquals( "7", tuple.getKey() );
        assertEquals( "7", tuple.getValue() );
    
        cursor.last();
        cursor.next();
        tuple = cursor.get();
        assertEquals( "9", tuple.getKey() );
        assertEquals( "9", tuple.getValue() );
    
        cursor.beforeFirst();
        cursor.next();
        tuple = cursor.get();
        assertEquals( "1", tuple.getKey() );
        assertEquals( "1", tuple.getValue() );
    
        cursor.afterLast();
        assertFalse( cursor.next() );

        cursor.beforeFirst();
        assertFalse( cursor.previous() );
        
        // just to clear the jdbmTuple value so that line 127 inside after(tuple) method
        // can be executed as part of the below after(tuple) call
        cursor.before(new Tuple<String,String>( "1", "1" )); 
        cursor.after( new Tuple<String,String>( "0", "0" ) );
        
        cursor.next();
        tuple = cursor.get();
        assertEquals( "1", tuple.getKey() );
        assertEquals( "1", tuple.getValue() );
    }
    

    @Test
    public void testJdbmBrowserSwitch() throws Exception
    {
        for( int i=1; i < 10; i++ )
        {
            String istr = Integer.toString( i );
            table.put( istr, istr );
        }
    
        Cursor<Tuple<String,String>> cursor = table.cursor();
        
        // go to last and call next then previous twice then next
        cursor.afterLast();
        assertFalse( cursor.next() );
        assertTrue( cursor.previous() );
        assertEquals( "9", cursor.get().getKey() );
        
        assertTrue( cursor.previous() );
        assertEquals( "8", cursor.get().getKey() );

        assertTrue( cursor.next() );
         assertEquals( "9", cursor.get().getKey() );
 
        
        // go to last and call previous then next and again previous 
        cursor.afterLast();
        assertTrue( cursor.previous() );
        assertEquals( "9", cursor.get().getKey() );
        
        assertTrue( cursor.next() );
        assertEquals( "9", cursor.get().getKey() );
        
        assertTrue( cursor.previous() );
        assertEquals( "8", cursor.get().getKey() );
        
        
        // go to first and call previous then next twice and again next
        cursor.beforeFirst();
        assertFalse( cursor.previous() );
        assertTrue( cursor.next() );
        assertEquals( "1", cursor.get().getKey() );

        assertTrue( cursor.next() );
        assertEquals( "2", cursor.get().getKey() );
        
        assertTrue( cursor.previous() );
        assertEquals( "1", cursor.get().getKey() );


        // go to first and call next twice then previous
        cursor.beforeFirst();
        assertTrue( cursor.next() );
        assertEquals( "1", cursor.get().getKey() );

        assertTrue( cursor.next() );
        assertEquals( "2", cursor.get().getKey() );
        
        assertTrue( cursor.previous() );
        assertEquals( "1", cursor.get().getKey() );

    }
    
    
    @Test
    public void testMiscellaneous() throws Exception
    {
    }
}
