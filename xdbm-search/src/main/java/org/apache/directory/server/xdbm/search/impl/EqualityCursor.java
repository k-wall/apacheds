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


import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.AbstractIndexCursor;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.Value;


/**
 * A Cursor over entry candidates matching an equality assertion filter.  This
 * Cursor operates in two modes.  The first is when an index exists for the
 * attribute the equality assertion is built on.  The second is when the user
 * index for the assertion attribute does not exist.  Different Cursors are
 * used in each of these cases where the other remains null.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $$Rev$$
 */
public class EqualityCursor<V, ID> extends AbstractIndexCursor<V, ServerEntry, ID>
{
    private static final String UNSUPPORTED_MSG = I18n.err( I18n.ERR_714 );

    /** An equality evaluator for candidates */
    @SuppressWarnings("unchecked")
    private final EqualityEvaluator equalityEvaluator;

    /** Cursor over attribute entry matching filter: set when index present */
    private final IndexCursor<V, ServerEntry, ID> userIdxCursor;

    /** NDN Cursor on all entries in  (set when no index on user attribute) */
    private final IndexCursor<String, ServerEntry, ID> ndnIdxCursor;

    /** used only when ndnIdxCursor is used (no index on attribute) */
    private boolean available = false;


    @SuppressWarnings("unchecked")
    public EqualityCursor( Store<ServerEntry, ID> db, EqualityEvaluator<V, ID> equalityEvaluator ) throws Exception
    {
        this.equalityEvaluator = equalityEvaluator;

        String attribute = equalityEvaluator.getExpression().getAttribute();
        Value<V> value = equalityEvaluator.getExpression().getValue();
        if ( db.hasIndexOn( attribute ) )
        {
            Index<V, ServerEntry, ID> userIndex = ( Index<V, ServerEntry, ID> ) db.getIndex( attribute );
            userIdxCursor = userIndex.forwardCursor( value.get() );
            ndnIdxCursor = null;
        }
        else
        {
            ndnIdxCursor = db.getNdnIndex().forwardCursor();
            userIdxCursor = null;
        }
    }


    public boolean available()
    {
        if ( userIdxCursor != null )
        {
            return userIdxCursor.available();
        }

        return available;
    }


    public void beforeValue( ID id, V value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.beforeValue( id, value );
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    public void before( IndexEntry<V, ServerEntry, ID> element ) throws Exception
    {
        checkNotClosed( "before()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.before( element );
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    public void afterValue( ID id, V key ) throws Exception
    {
        checkNotClosed( "afterValue()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.afterValue( id, key );
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    public void after( IndexEntry<V, ServerEntry, ID> element ) throws Exception
    {
        checkNotClosed( "after()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.after( element );
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.beforeFirst();
        }
        else
        {
            ndnIdxCursor.beforeFirst();
            available = false;
        }
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.afterLast();
        }
        else
        {
            ndnIdxCursor.afterLast();
            available = false;
        }
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
        if ( userIdxCursor != null )
        {
            return userIdxCursor.previous();
        }

        while ( ndnIdxCursor.previous() )
        {
            checkNotClosed( "previous()" );
            IndexEntry<?, ServerEntry, ID> candidate = ndnIdxCursor.get();
            if ( equalityEvaluator.evaluate( candidate ) )
            {
                return available = true;
            }
        }

        return available = false;
    }


    @SuppressWarnings("unchecked")
    public boolean next() throws Exception
    {
        if ( userIdxCursor != null )
        {
            return userIdxCursor.next();
        }

        while ( ndnIdxCursor.next() )
        {
            checkNotClosed( "next()" );
            IndexEntry<?, ServerEntry, ID> candidate = ndnIdxCursor.get();
            if ( equalityEvaluator.evaluate( candidate ) )
            {
                return available = true;
            }
        }

        return available = false;
    }


    @SuppressWarnings("unchecked")
    public IndexEntry<V, ServerEntry, ID> get() throws Exception
    {
        checkNotClosed( "get()" );
        if ( userIdxCursor != null )
        {
            return userIdxCursor.get();
        }

        if ( available )
        {
            return ( IndexEntry<V, ServerEntry, ID> ) ndnIdxCursor.get();
        }

        throw new InvalidCursorPositionException( I18n.err( I18n.ERR_708 ) );
    }


    public boolean isElementReused()
    {
        if ( userIdxCursor != null )
        {
            return userIdxCursor.isElementReused();
        }

        return ndnIdxCursor.isElementReused();
    }


    public void close() throws Exception
    {
        super.close();

        if ( userIdxCursor != null )
        {
            userIdxCursor.close();
        }
        else
        {
            ndnIdxCursor.close();
        }
    }
}
