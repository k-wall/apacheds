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
package org.apache.directory.server.core.avltree;


import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.AbstractTupleCursor;
import org.apache.directory.server.xdbm.Tuple;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;


/**
 * Cursor over a set of values for the same key which are store in an in
 * memory AvlTree.  This Cursor is limited to the same key and it's tuples
 * will always return the same key.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class KeyTupleAvlCursor<K,V> extends AbstractTupleCursor<K,V>
{
    private final AvlTreeCursor<V> wrapped;
    private final K key;

    private Tuple<K,V> returnedTuple = new Tuple<K,V>();
    private boolean valueAvailable;


    /**
     * Creates a Cursor over the tuples of an AvlTree.
     *
     * @param avlTree the AvlTree to build a Tuple returning Cursor over
     * @param key the constant key for which values are returned
     */
    public KeyTupleAvlCursor( AvlTree<V> avlTree, K key )
    {
        this.key = key;
        this.wrapped = new AvlTreeCursor<V>( avlTree );
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

        wrapped.before( value );
        clearValue();
    }


    public void afterValue( K key, V value ) throws Exception
    {
        checkNotClosed( "afterValue()" );
        if ( key != null && ! key.equals( this.key ) )
        {
            throw new UnsupportedOperationException( I18n.err( I18n.ERR_446 ) );
        }

        wrapped.after( value );
        clearValue();
    }


    /**
     * Positions this Cursor over the same keys before the value of the
     * supplied element Tuple.  The supplied element Tuple's key is not
     * considered at all.
     *
     * @param element the valueTuple who's value is used to position this Cursor
     * @throws Exception if there are failures to position the Cursor
     */
    public void before( Tuple<K,V> element ) throws Exception
    {
        checkNotClosed( "before()" );
        wrapped.before( element.getValue() );
        clearValue();
    }


    public void after( Tuple<K,V> element ) throws Exception
    {
        checkNotClosed( "after()" );
        wrapped.after( element.getValue() );
        clearValue();
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        wrapped.beforeFirst();
        clearValue();
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        wrapped.afterLast();
        clearValue();
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


    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        if ( wrapped.previous() )
        {
            returnedTuple.setKey( key );
            returnedTuple.setValue( wrapped.get() );
            return valueAvailable = true;
        }
        else
        {
            clearValue();
            return false;
        }
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        if ( wrapped.next() )
        {
            returnedTuple.setKey( key );
            returnedTuple.setValue( wrapped.get() );
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