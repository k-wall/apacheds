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
package org.apache.directory.server.core.partition.impl.btree.jdbm;


import jdbm.btree.BTree;

import org.apache.directory.server.core.avltree.ArrayTree;
import org.apache.directory.server.core.avltree.ArrayTreeCursor;
import org.apache.directory.server.xdbm.Tuple;
import org.apache.directory.server.xdbm.AbstractTupleCursor;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Cursor over a BTree which manages duplicate keys.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
class DupsCursor<K,V> extends AbstractTupleCursor<K,V>
{
    private static final Logger LOG = LoggerFactory.getLogger( DupsCursor.class.getSimpleName() );
    
    /**
     * The JDBM backed table this Cursor traverses over.
     */
    private final JdbmTable<K,V> table;

    /**
     * An wrappedCursor Cursor which returns Tuples whose values are
     * DupsContainer objects representing either AvlTrees or BTreeRedirect
     * objects used to store the values of duplicate keys.  It does not return
     * different values for the same key.
     */
    private final DupsContainerCursor<K,V> containerCursor;

    /**
     * The current Tuple returned from the wrappedCursor DupsContainerCursor.
     */
    private final Tuple<K,DupsContainer<V>> containerTuple = new Tuple<K, DupsContainer<V>>();

    /**
     * A Cursor over a set of value objects for the current key held in the
     * containerTuple.  A new Cursor will be set for each new key as we
     * traverse.  The Cursor traverses over either a AvlTree object full
     * of values in a multi-valued key or it traverses over a BTree which
     * contains the values in the key field of it's Tuples.
     */
    private Cursor<V> dupsCursor;

    /**
     * The Tuple that is used to return values via the get() method. This
     * same Tuple instance will be returned every time.  At different
     * positions it may return different values for the same key.
     */
    private final Tuple<K,V> returnedTuple = new Tuple<K,V>();

    /**
     * Whether or not a value is available when get() is called.
     */
    private boolean valueAvailable;


    public DupsCursor( JdbmTable<K,V> table ) throws Exception
    {
        this.table = table;
        this.containerCursor = new DupsContainerCursor<K,V>( table );
        LOG.debug( "Created on table {}", table );
    }


    public boolean available()
    {
        return valueAvailable;
    }


    public void beforeKey( K key ) throws Exception
    {
        beforeValue( key, null );
    }


    public void beforeValue( K key, V value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );
        containerCursor.before( new Tuple<K,DupsContainer<V>>( key, null ) );

        if ( containerCursor.next() )
        {
            containerTuple.setBoth( containerCursor.get() );
            DupsContainer<V> values = containerTuple.getValue();

            if ( values.isArrayTree() )
            {
                ArrayTree<V> set = values.getArrayTree();
                dupsCursor = new ArrayTreeCursor<V>( set );
            }
            else
            {
                BTree tree = table.getBTree( values.getBTreeRedirect() );
                dupsCursor = new KeyBTreeCursor<V>( tree, table.getValueComparator() );
            }

            if ( value == null )
            {
                return;
            }

            // advance the dupsCursor only if we're on same key
            if ( table.getKeyComparator().compare( containerTuple.getKey(), key ) == 0 )
            {
                dupsCursor.before( value );
            }

            return;
        }

        clearValue();
        containerTuple.setKey( null );
        containerTuple.setValue( null );
    }


    public void afterKey( K key ) throws Exception
    {
        afterValue( key, null );
    }


    public void afterValue( K key, V value ) throws Exception
    {
        checkNotClosed( "afterValue()" );
        /*
         * There is a subtle difference between after and before handling
         * with duplicate key values.  Say we have the following tuples:
         *
         * (0, 0)
         * (1, 1)
         * (1, 2)
         * (1, 3)
         * (2, 2)
         *
         * If we request an after cursor on (1, 2).  We must make sure that
         * the container cursor does not advance after the entry with key 1
         * since this would result in us skip returning (1. 3) on the call to
         * next which will incorrectly return (2, 2) instead.
         *
         * So if the value is null in the element then we don't care about
         * this obviously since we just want to advance past the duplicate key
         * values all together.  But when it is not null, then we want to
         * go right before this key instead of after it.
         */

        if ( value == null )
        {
            containerCursor.after( new Tuple<K,DupsContainer<V>>( key, null ) );
        }
        else
        {
            containerCursor.before( new Tuple<K,DupsContainer<V>>( key, null ) );
        }

        if ( containerCursor.next() )
        {
            containerTuple.setBoth( containerCursor.get() );
            DupsContainer<V> values = containerTuple.getValue();

            if ( values.isArrayTree() )
            {
                ArrayTree<V> set = values.getArrayTree();
                dupsCursor = new ArrayTreeCursor<V>( set );
            }
            else
            {
                BTree tree = table.getBTree( values.getBTreeRedirect() );
                dupsCursor = new KeyBTreeCursor<V>( tree, table.getValueComparator() );
            }

            if ( value == null )
            {
                return;
            }

            // only advance the dupsCursor if we're on same key
            if ( table.getKeyComparator().compare( containerTuple.getKey(), key ) == 0 )
            {
                dupsCursor.after( value );
            }

            return;
        }

        clearValue();
        containerTuple.setKey( null );
        containerTuple.setValue( null );
    }


    public void before( Tuple<K,V> element ) throws Exception
    {
        beforeValue( element.getKey(), element.getValue() );
    }


    public void after( Tuple<K,V> element ) throws Exception
    {
        afterValue( element.getKey(), element.getValue() );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        clearValue();
        containerCursor.beforeFirst();
        containerTuple.setKey( null );
        containerTuple.setValue( null );
        dupsCursor = null;
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        clearValue();
        containerCursor.afterLast();
        containerTuple.setKey( null );
        containerTuple.setValue( null );
        dupsCursor = null;
    }


    public boolean first() throws Exception
    {
        checkNotClosed( "first()" );
        clearValue();
        dupsCursor = null;

        if ( containerCursor.first() )
        {
            containerTuple.setBoth( containerCursor.get() );
            DupsContainer<V> values = containerTuple.getValue();

            if ( containerTuple.getValue().isArrayTree() )
            {
                dupsCursor = new ArrayTreeCursor<V>( values.getArrayTree() );
            }
            else
            {
                BTree bt = table.getBTree( values.getBTreeRedirect() );
                dupsCursor = new KeyBTreeCursor<V>( bt, table.getValueComparator() );
            }

            /*
             * Since only tables with duplicate keys enabled use this
             * cursor, entries must have at least one value, and therefore
             * call to last() will always return true.
             */
            dupsCursor.first();
            valueAvailable =  true;
            returnedTuple.setKey( containerTuple.getKey() );
            returnedTuple.setValue( dupsCursor.get() );
            return true;
        }

        return false;
    }


    public boolean last() throws Exception
    {
        checkNotClosed( "last()" );
        clearValue();
        dupsCursor = null;

        if ( containerCursor.last() )
        {
            containerTuple.setBoth( containerCursor.get() );
            DupsContainer<V> values = containerTuple.getValue();

            if ( values.isArrayTree() )
            {
                ArrayTree<V> set = values.getArrayTree();
                dupsCursor = new ArrayTreeCursor<V>( set );
            }
            else
            {
                BTree tree = table.getBTree( values.getBTreeRedirect() );
                dupsCursor = new KeyBTreeCursor<V>( tree, table.getValueComparator() );
            }

            /*
             * Since only tables with duplicate keys enabled use this
             * cursor, entries must have at least one value, and therefore
             * call to last() will always return true.
             */
            dupsCursor.last();
            valueAvailable = true;
            returnedTuple.setKey( containerTuple.getKey() );
            returnedTuple.setValue( dupsCursor.get() );
            return true;
        }

        return false;
    }



    private void clearValue()
    {
        returnedTuple.setKey( null );
        returnedTuple.setValue( null );
        valueAvailable = false;
    }


    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        /*
         * If the iterator over the values of the current key is null or is
         * extinguished then we need to advance to the previous key.
         */
        if ( null == dupsCursor || ! dupsCursor.previous() )
        {
            /*
             * If the wrappedCursor cursor has more elements we get the previous
             * key/AvlTree Tuple to work with and get a cursor over it's
             * values.
             */
            if ( containerCursor.previous() )
            {
                containerTuple.setBoth( containerCursor.get() );
                DupsContainer<V> values = containerTuple.getValue();

                if ( values.isArrayTree() )
                {
                    ArrayTree<V> set = values.getArrayTree();
                    dupsCursor = new ArrayTreeCursor<V>( set );
                }
                else
                {
                    BTree tree = table.getBTree( values.getBTreeRedirect() );
                    dupsCursor = new KeyBTreeCursor<V>( tree, table.getValueComparator() );
                }

                /*
                 * Since only tables with duplicate keys enabled use this
                 * cursor, entries must have at least one value, and therefore
                 * call to previous() after bringing the cursor to afterLast()
                 * will always return true.
                 */
                dupsCursor.afterLast();
                dupsCursor.previous();
            }
            else
            {
                dupsCursor = null;
                return false;
            }
        }

        returnedTuple.setKey( containerTuple.getKey() );
        returnedTuple.setValue( dupsCursor.get() );
        return valueAvailable = true;
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        /*
         * If the iterator over the values of the current key is null or is
         * extinguished then we need to advance to the next key.
         */
        if ( null == dupsCursor || ! dupsCursor.next() )
        {
            /*
             * If the wrappedCursor cursor has more elements we get the next
             * key/AvlTree Tuple to work with and get a cursor over it.
             */
            if ( containerCursor.next() )
            {
                containerTuple.setBoth( containerCursor.get() );
                DupsContainer<V> values = containerTuple.getValue();

                if ( values.isArrayTree() )
                {
                    ArrayTree<V> set = values.getArrayTree();
                    dupsCursor = new ArrayTreeCursor<V>( set );
                }
                else
                {
                    BTree tree = table.getBTree( values.getBTreeRedirect() );
                    dupsCursor = new KeyBTreeCursor<V>( tree, table.getValueComparator() );
                }

                /*
                 * Since only tables with duplicate keys enabled use this
                 * cursor, entries must have at least one value, and therefore
                 * call to next() after bringing the cursor to beforeFirst()
                 * will always return true.
                 */
                dupsCursor.beforeFirst();
                dupsCursor.next();
            }
            else
            {
                dupsCursor = null;
                return false;
            }
        }

        /*
         * If we get to this point then cursor has more elements and
         * containerTuple holds the Tuple containing the key and the btree or
         * AvlTree of values for that key which the Cursor traverses.  All we
         * need to do is populate our tuple object with the key and the value
         * in the cursor.
         */
        returnedTuple.setKey( containerTuple.getKey() );
        returnedTuple.setValue( dupsCursor.get() );
        return valueAvailable = true;
    }


    public Tuple<K,V> get() throws Exception
    {
        checkNotClosed( "get()" );

        if ( ! valueAvailable )
        {
            throw new InvalidCursorPositionException();
        }

        return returnedTuple;
    }


    public boolean isElementReused()
    {
        return true;
    }
}
