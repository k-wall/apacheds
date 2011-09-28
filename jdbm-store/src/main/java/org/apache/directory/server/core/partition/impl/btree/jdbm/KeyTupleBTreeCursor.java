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


import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.Tuple;
import org.apache.directory.server.xdbm.AbstractTupleCursor;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;

import java.util.Comparator;

import jdbm.helper.TupleBrowser;
import jdbm.btree.BTree;


/**
 * Cursor over a set of values for the same key which are store in another
 * BTree.  This Cursor is limited to the same key and it's tuples will always
 * return the same key.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class KeyTupleBTreeCursor<K,V> extends AbstractTupleCursor<K,V>
{
    private final Comparator<V> comparator;
    private final BTree btree;
    private final K key;

    private jdbm.helper.Tuple valueTuple = new jdbm.helper.Tuple();
    private Tuple<K,V> returnedTuple = new Tuple<K,V>();
    private TupleBrowser browser;
    private boolean valueAvailable;


    /**
     * Creates a Cursor over the tuples of a JDBM BTree.
     *
     * @param btree the JDBM BTree to build a Cursor over
     * @param key the constant key for which values are returned
     * @param comparator the Comparator used to determine <b>key</b> ordering
     * @throws Exception of there are problems accessing the BTree
     */
    public KeyTupleBTreeCursor( BTree btree, K key, Comparator<V> comparator ) throws Exception
    {
        this.key = key;
        this.btree = btree;
        this.comparator = comparator;
        this.browser = btree.browse();
    }


    private void clearValue()
    {
        returnedTuple.setKey( key );
        returnedTuple.setValue( null );
        valueAvailable = false;
    }


    public boolean available()
    {
        return valueAvailable;
    }


    public void beforeKey( K key ) throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_446 ) );
    }


    public void afterKey( K key ) throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_446 ) );
    }


    public void beforeValue( K key, V value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );
        if ( key != null && ! key.equals( this.key ) )
        {
            throw new UnsupportedOperationException( I18n.err( I18n.ERR_446 ) );
        }

        browser = btree.browse( value );
        clearValue();
    }


    @SuppressWarnings("unchecked")
    public void afterValue( K key, V value ) throws Exception
    {
        if ( key != null && ! key.equals( this.key ) )
        {
            throw new UnsupportedOperationException( I18n.err( I18n.ERR_446 ) );
        }

        browser = btree.browse( value );

        /*
         * While the next value is less than or equal to the element keep
         * advancing forward to the next item.  If we cannot advance any
         * further then stop and return.  If we find a value greater than
         * the element then we stop, backup, and return so subsequent calls
         * to getNext() will return a value greater than the element.
         */
        while ( browser.getNext( valueTuple ) )
        {
            checkNotClosed( "afterValue" );

            V next = ( V ) valueTuple.getKey();

            int nextCompared = comparator.compare( next, value );

            if ( nextCompared <= 0 )
            {
                // just continue
            }
            else if ( nextCompared > 0 )
            {
                /*
                 * If we just have values greater than the element argument
                 * then we are before the first element and cannot backup, and
                 * the call below to getPrevious() will fail.  In this special
                 * case we just reset the Cursor's browser and return.
                 */
                if ( browser.getPrevious( valueTuple ) )
                {
                }
                else
                {
                    browser = btree.browse( this.key );
                }

                clearValue();
                return;
            }
        }

        clearValue();
    }


    /**
     * Positions this Cursor over the same keys before the value of the
     * supplied valueTuple.  The supplied element Tuple's key is not considered at
     * all.
     *
     * @param element the valueTuple who's value is used to position this Cursor
     * @throws Exception if there are failures to position the Cursor
     */
    public void before( Tuple<K,V> element ) throws Exception
    {
        checkNotClosed( "before()" );
        browser = btree.browse( element.getValue() );
        clearValue();
    }


    public void after( Tuple<K,V> element ) throws Exception
    {
        afterValue( key, element.getValue() );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        browser = btree.browse();
        clearValue();
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        browser = btree.browse( null );
    }


    public boolean first() throws Exception
    {
        beforeFirst();
        return next();
    }


    public boolean last() throws Exception
    {
        afterLast();
        return previous();
    }


    @SuppressWarnings("unchecked")
    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        if ( browser.getPrevious( valueTuple ) )
        {
            // work around to fix direction change problem with jdbm browser
            if ( returnedTuple.getValue() != null &&
                comparator.compare( ( V ) valueTuple.getKey(), returnedTuple.getValue() ) == 0 )
            {
                browser.getPrevious( valueTuple ) ;
            }
            returnedTuple.setKey( key );
            returnedTuple.setValue( ( V ) valueTuple.getKey() );
            return valueAvailable = true;
        }
        else
        {
            clearValue();
            return false;
        }
    }


    @SuppressWarnings("unchecked")
    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        if ( browser.getNext( valueTuple ) )
        {
            // work around to fix direction change problem with jdbm browser
            if ( returnedTuple.getValue() != null &&
                 comparator.compare( ( V ) valueTuple.getKey(), returnedTuple.getValue() ) == 0 )
            {
                browser.getNext( valueTuple ) ;
            }
            returnedTuple.setKey( key );
            returnedTuple.setValue( ( V ) valueTuple.getKey() );
            return valueAvailable = true;
        }
        else
        {
            clearValue();
            return false;
        }
    }


    public Tuple<K,V> get() throws Exception
    {
        checkNotClosed( "get()" );
        if ( valueAvailable )
        {
            return returnedTuple;
        }

        throw new InvalidCursorPositionException();
    }


    public boolean isElementReused()
    {
        return true;
    }
}
