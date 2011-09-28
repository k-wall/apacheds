/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core.avltree;

 
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * TestCase for AvlTreeMarshaller.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class AvlTreeMarshallerTest
{
    private static final long[] AVLTREE_KEYS_PRE_REMOVE =
    {
        2, 14, 26, 86, 110, 122, 134, 182
    };
        
    private static final long[] AVLTREE_EXPECTED_KEYS_POST_REMOVE =
    {
        2, 14, 26, 86, 122, 134, 182
    };
        
    private static final byte[] SERIALIZED_AVLTREE_PRE_REMOVE =
    {
        0, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 
        110, 0, 0, 0, 4, 0, 0, 0, 2, 0, 0, 0, 8, 0, 0, 0, 
        0, 0, 0, 0, 26, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 
        8, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 
        0, 0, 0, 0, 4, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 
        14, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
        4, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 86, 0, 0, 0, 
        3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 
        8, 0, 0, 0, 0, 0, 0, 0, -122, 0, 0, 0, 6, 0, 0, 0, 
        2, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 122, 0, 0, 0, 
        5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 
        8, 0, 0, 0, 0, 0, 0, 0, -74, 0, 0, 0, 7, 0, 0, 0, 
        0, 0, 0, 0, 0 
    };
    
//    private static final byte[] SERIALIZED_AVLTREE_POST_REMOVE = 
//    { 
//        0, 0, 0, 0, 7, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 
//        86, 0, 0, 0, 3, 0, 0, 0, 2, 0, 0, 0, 8, 0, 0, 0, 
//        0, 0, 0, 0, 26, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 
//        0, 0, 0, 0, 4, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, -122, 
//        0, 0, 0, 5, 0, 0, 0, 2, 0, 0, 0, 8, 0, 0, 0, 0, 
//        0, 0, 0, 122, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 
//        0, 0, 0, 4, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, -74, 
//        0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0 
//    };

    AvlTree<Integer> tree;
    Comparator<Integer> comparator;
    AvlTreeMarshaller<Integer> treeMarshaller;
    
    static AvlTree<Integer> savedTree;
    
    static File treeFile = new File( System.getProperty( "java.io.tmpdir" ) + File.separator + "avl.tree");
    
    private static final Logger LOG = LoggerFactory.getLogger( AvlTreeMarshallerTest.class.getSimpleName() );

    
    @Before
    public void createTree()
    {
        comparator = new Comparator<Integer>() 
        {
            public int compare( Integer i1, Integer i2 )
            {
                return i1.compareTo( i2 );
            }
        };
        
      
        tree = new AvlTreeImpl<Integer>( comparator );
        treeMarshaller = new AvlTreeMarshaller<Integer>( comparator, new IntegerKeyMarshaller() );
    }

    
    @AfterClass
    public static void deleteFiles()
    {
        treeFile.delete();
    }
    
    
    @Test
    public void testRemoveBug() throws IOException
    {
        Comparator<Long> comparator = new Comparator<Long>() 
        {
            public int compare( Long i1, Long i2 )
            {
                return i1.compareTo( i2 );
            }
        };

        /*
         * This deserializes the state of the AvlTree before the remove 
         * operation and it should work checking to make sure that all
         * the pre-remove keys are present.
         */

        AvlTreeMarshaller<Long> treeMarshaller = new AvlTreeMarshaller<Long>( comparator, new LongMarshaller() );
        AvlTree<Long> tree = treeMarshaller.deserialize( SERIALIZED_AVLTREE_PRE_REMOVE );
        
        for ( long key : AVLTREE_KEYS_PRE_REMOVE )
        {
            assertNotNull( "Should find " + key, tree.find( key ) );
        }
        
        /*
         * Now we remove the key 110 and this should show that we don't have 
         * the expected keys.  We will be missing 134, 2 and 14.
         */
        
        tree.remove( 110L );

        for ( long key : AVLTREE_EXPECTED_KEYS_POST_REMOVE )
        {
            assertNotNull( "Should find " + key, tree.find( key ) );
        }
    }
    

    @Test
    public void testMarshalEmptyTree() throws IOException
    {
        byte[] bites = treeMarshaller.serialize( new AvlTreeImpl<Integer>( comparator ) );
        AvlTree<Integer> tree = treeMarshaller.deserialize( bites );
        assertNotNull( tree );
    }


    @Test
    public void testRoundTripEmpty() throws IOException
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertTrue( deserialized.isEmpty() );
    }


    @Test
    public void testRoundTripOneEntry() throws IOException
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        original.insert( 0 );
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 1, deserialized.getSize() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );
    }


    @Test
    public void testRoundTripOneEntryFirstLast() throws IOException
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        original.insert( 0 );
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 1, deserialized.getSize() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );

        assertNotNull( original.getFirst() );
        assertEquals( 0, ( int ) original.getFirst().getKey() );

        assertNotNull( deserialized.getFirst() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );

        assertNotNull( original.getLast() );
        assertEquals( 0, ( int ) original.getLast().getKey() );

        // this marshaller fails to preserve last node reference
        assertNotNull( deserialized.getLast() );
        assertEquals( 0, ( int ) deserialized.getLast().getKey() );
    }


    @Test
    public void testRoundTripTwoEntries() throws IOException
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        original.insert( 0 );
        original.insert( 1 );
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 2, deserialized.getSize() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );
        assertEquals( 1, ( int ) deserialized.getFirst().next.getKey() );
    }


    @Test
    public void testRoundTripTwoEntriesFirstLast() throws IOException
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        original.insert( 0 );
        original.insert( 1 );
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 2, deserialized.getSize() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );
        assertEquals( 1, ( int ) deserialized.getFirst().next.getKey() );

        assertNotNull( original.getFirst() );
        assertEquals( 0, ( int ) original.getFirst().getKey() );

        assertNotNull( deserialized.getFirst() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );

        assertNotNull( original.getLast() );
        assertEquals( 1, ( int ) original.getLast().getKey() );

        // this marshaller fails to preserve last node reference
        assertNotNull( deserialized.getLast() );
        assertEquals( 1, ( int ) deserialized.getLast().getKey() );
    }


    @Test
    public void testRoundTripManyEntries() throws Exception
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        for ( int ii = 0; ii < 100; ii++ )
        {
            original.insert( ii );
        }
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 100, deserialized.getSize() );

        AvlTreeCursor<Integer> cursor = new AvlTreeCursor<Integer>( deserialized );
        cursor.first();
        for ( int ii = 0; ii < 100; ii++ )
        {
            assertEquals( ii, ( int ) cursor.get() );
            cursor.next();
        }
    }


    @Test
    public void testRoundTripManyEntriesFirstLast() throws Exception
    {
        AvlTree<Integer> original = new AvlTreeImpl<Integer>( comparator );
        for ( int ii = 0; ii < 100; ii++ )
        {
            original.insert( ii );
        }
        byte[] bites = treeMarshaller.serialize( original );
        AvlTree<Integer> deserialized = treeMarshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 100, deserialized.getSize() );

        AvlTreeCursor<Integer> cursor = new AvlTreeCursor<Integer>( deserialized );
        cursor.first();
        for ( int ii = 0; ii < 100; ii++ )
        {
            assertEquals( ii, ( int ) cursor.get() );
            cursor.next();
        }

        assertNotNull( original.getFirst() );
        assertEquals( 0, ( int ) original.getFirst().getKey() );

        assertNotNull( deserialized.getFirst() );
        assertEquals( 0, ( int ) deserialized.getFirst().getKey() );

        assertNotNull( original.getLast() );
        assertEquals( 99, ( int ) original.getLast().getKey() );

        // this marshaller fails to preserve last node reference
        assertNotNull( deserialized.getLast() );
        assertEquals( 99, ( int ) deserialized.getLast().getKey() );
    }


    @Test
    public void testRoundTripManyEntriesDefaultSerialization() throws Exception
    {
        Comparator<Bar> barComparator = new Comparator<Bar>() {
            public int compare( Bar o1, Bar o2 )
            {
                return o1.intValue.compareTo( o2.intValue );
            }
        };

        AvlTree<Bar> original = new AvlTreeImpl<Bar>( barComparator );

        for ( int ii = 0; ii < 100; ii++ )
        {
            original.insert( new Bar( ii ) );
        }

        AvlTreeMarshaller<Bar> marshaller = new AvlTreeMarshaller<Bar>( barComparator );
        byte[] bites = marshaller.serialize( original );
        AvlTree<Bar> deserialized = marshaller.deserialize( bites );
        assertFalse( deserialized.isEmpty() );
        assertEquals( 100, deserialized.getSize() );

        AvlTreeCursor<Bar> cursor = new AvlTreeCursor<Bar>( deserialized );
        cursor.first();
        for ( int ii = 0; ii < 100; ii++ )
        {
            assertEquals( ii, ( int ) cursor.get().intValue );
            cursor.next();
        }
    }


    static class Bar implements Serializable
    {
        Integer intValue = 37;
        String stringValue = "bar";
        long longValue = 32L;
        Foo fooValue = new Foo();


        public Bar( int ii )
        {
            intValue = ii;
        }
    }


    static class Foo implements Serializable
    {
        float floatValue = 3;
        String stringValue = "foo";
        double doubleValue = 1.2;
        byte byteValue = 3;
        char charValue = 'a';
    }


    @Test
    public void testMarshal() throws IOException
    {
        tree.insert( 37 );
        tree.insert( 7 );
        tree.insert( 25 );
        tree.insert( 8 );
        tree.insert( 9 );

        FileOutputStream fout = new FileOutputStream( treeFile );
        fout.write( treeMarshaller.serialize( tree ) );
        fout.close();
        
        savedTree = tree; // to reference in other tests
        
        if( LOG.isDebugEnabled() )
        {
            LOG.debug("saved tree\n--------");
            tree.printTree();
        }
        
        assertTrue( true );
    }


    @Test
    public void testUnMarshal() throws FileNotFoundException, IOException
    {
        FileInputStream fin = new FileInputStream(treeFile);
        
        byte[] data = new byte[ ( int )treeFile.length() ];
        fin.read( data );
        
        AvlTree<Integer> unmarshalledTree = treeMarshaller.deserialize( data );
        
        if( LOG.isDebugEnabled() )
        {
            LOG.debug("\nunmarshalled tree\n---------------");
            unmarshalledTree.printTree();
        }
        
        assertTrue( savedTree.getRoot().getKey() == unmarshalledTree.getRoot().getKey() );

        unmarshalledTree.insert( 6 ); // will change the root as part of balancing
        
        assertTrue( savedTree.getRoot().getKey() == unmarshalledTree.getRoot().getKey() );
        assertTrue( 8 == unmarshalledTree.getRoot().getKey() ); // new root
        
        assertTrue( 37 == unmarshalledTree.getLast().getKey() );
        unmarshalledTree.insert( 99 );
        assertTrue( 99 == unmarshalledTree.getLast().getKey() );

        assertTrue( 6 == unmarshalledTree.getFirst().getKey() );
        
        unmarshalledTree.insert( 0 );
        assertTrue( 0 == unmarshalledTree.getFirst().getKey() );
        
        if( LOG.isDebugEnabled() )
        {
            LOG.debug("\nmodified tree after unmarshalling\n---------------");
            unmarshalledTree.printTree();
        }
        
        assertNotNull(unmarshalledTree.getFirst());
        assertNotNull(unmarshalledTree.getLast());
    }
    
    
    @Test( expected = IOException.class )
    public void testDeserializeNullData() throws IOException
    {
        treeMarshaller.deserialize( null );
    }
    
    
    public class LongMarshaller implements Marshaller<Long>
    {
        public byte[] serialize( Long obj ) throws IOException
        {
            long id = obj.longValue();
            byte[] bites = new byte[8];

            bites[0] = ( byte ) ( id >> 56 );
            bites[1] = ( byte ) ( id >> 48 );
            bites[2] = ( byte ) ( id >> 40 );
            bites[3] = ( byte ) ( id >> 32 );
            bites[4] = ( byte ) ( id >> 24 );
            bites[5] = ( byte ) ( id >> 16 );
            bites[6] = ( byte ) ( id >> 8 );
            bites[7] = ( byte ) id;

            return bites;
        }


        public Long deserialize( byte[] bites ) throws IOException
        {
            long id;
            id = bites[0]  + ( ( bites[0] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[1] + ( ( bites[1] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[2] + ( ( bites[2] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[3] + ( ( bites[3] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[4] + ( ( bites[4] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[5] + ( ( bites[5] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[6] + ( ( bites[6] < 0 ) ? 256 : 0 );
            id <<= 8;
            id += bites[7] + ( ( bites[7] < 0 ) ? 256 : 0 );
            return id;
        }
    }
}
