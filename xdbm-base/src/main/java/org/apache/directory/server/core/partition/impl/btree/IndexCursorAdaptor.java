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
package org.apache.directory.server.core.partition.impl.btree;


import java.util.Iterator;

import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.ReverseIndexEntry;
import org.apache.directory.server.xdbm.Tuple;
import org.apache.directory.server.xdbm.TupleCursor;
import org.apache.directory.shared.ldap.cursor.ClosureMonitor;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.cursor.CursorIterator;


/**
 * A Cursor which adapts an underlying Tuple based Cursor to one which returns
 * IndexEntry objects rather than tuples.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class IndexCursorAdaptor<K, O, ID> implements IndexCursor<K, O, ID>
{
    @SuppressWarnings("unchecked")
    final Cursor<Tuple> wrappedCursor;
    final ForwardIndexEntry<K, O, ID> forwardEntry;
    final ReverseIndexEntry<K, O, ID> reverseEntry;


    /**
     * Creates an IndexCursorAdaptor which wraps and adapts a Cursor from a table to
     * one which returns an IndexEntry.
     *
     * @param wrappedCursor the Cursor being adapted
     * @param forwardIndex true for a cursor over a forward index, false for
     * one over a reverse index
     */
    @SuppressWarnings("unchecked")
    public IndexCursorAdaptor( Cursor<Tuple> wrappedCursor, boolean forwardIndex )
    {
        this.wrappedCursor = wrappedCursor;
        if ( forwardIndex )
        {
            forwardEntry = new ForwardIndexEntry<K, O, ID>();
            reverseEntry = null;
        }
        else
        {
            forwardEntry = null;
            reverseEntry = new ReverseIndexEntry<K, O, ID>();
        }
    }


    public boolean available()
    {
        return wrappedCursor.available();
    }


    @SuppressWarnings("unchecked")
    public void beforeValue( ID id, K key ) throws Exception
    {
        if ( wrappedCursor instanceof TupleCursor )
        {
            ( ( TupleCursor ) wrappedCursor ).beforeValue( key, id );
        }
    }


    @SuppressWarnings("unchecked")
    public void afterValue( ID id, K key ) throws Exception
    {
        if ( wrappedCursor instanceof TupleCursor )
        {
            ( ( TupleCursor ) wrappedCursor ).afterValue( key, id );
        }
    }


    public void before( IndexEntry<K, O, ID> element ) throws Exception
    {
        wrappedCursor.before( element.getTuple() );
    }


    public void after( IndexEntry<K, O, ID> element ) throws Exception
    {
        wrappedCursor.after( element.getTuple() );
    }


    public void beforeFirst() throws Exception
    {
        wrappedCursor.beforeFirst();
    }


    public void afterLast() throws Exception
    {
        wrappedCursor.afterLast();
    }


    public boolean first() throws Exception
    {
        return wrappedCursor.first();
    }


    public boolean last() throws Exception
    {
        return wrappedCursor.last();
    }


    public boolean isClosed() throws Exception
    {
        return wrappedCursor.isClosed();
    }


    public boolean previous() throws Exception
    {
        return wrappedCursor.previous();
    }


    public boolean next() throws Exception
    {
        return wrappedCursor.next();
    }


    @SuppressWarnings("unchecked")
    public IndexEntry<K, O, ID> get() throws Exception
    {
        if ( forwardEntry != null )
        {
            Tuple<K, ID> tuple = wrappedCursor.get();
            forwardEntry.setTuple( tuple, null );
            return forwardEntry;
        }
        else
        {
            Tuple<ID, K> tuple = wrappedCursor.get();
            reverseEntry.setTuple( tuple, null );
            return reverseEntry;
        }
    }


    public boolean isElementReused()
    {
        return true;
    }


    public final void setClosureMonitor( ClosureMonitor monitor )
    {
        wrappedCursor.setClosureMonitor( monitor );
    }


    public void close() throws Exception
    {
        wrappedCursor.close();
    }


    public void close( Exception reason ) throws Exception
    {
        wrappedCursor.close( reason );
    }


    public Iterator<IndexEntry<K, O, ID>> iterator()
    {
        return new CursorIterator<IndexEntry<K, O, ID>>( this );
    }
}
