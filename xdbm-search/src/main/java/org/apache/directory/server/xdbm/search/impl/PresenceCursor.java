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


import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.AbstractIndexCursor;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.schema.AttributeType;


/**
 * A returning candidates satisfying an attribute presence expression.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $$Rev$$
 */
public class PresenceCursor<ID> extends AbstractIndexCursor<String, ServerEntry, ID>
{
    private static final String UNSUPPORTED_MSG = I18n.err( I18n.ERR_724 );
    private final IndexCursor<String, ServerEntry, ID> ndnCursor;
    private final IndexCursor<String, ServerEntry, ID> presenceCursor;
    private final PresenceEvaluator<ID> presenceEvaluator;
    private boolean available = false;


    public PresenceCursor( Store<ServerEntry, ID> db, PresenceEvaluator<ID> presenceEvaluator ) throws Exception
    {
        this.presenceEvaluator = presenceEvaluator;
        AttributeType type = presenceEvaluator.getAttributeType();

        // we don't maintain a presence index for objectClass, entryUUID, and entryCSN
        // as it doesn't make sense because every entry has such an attribute
        // instead for those attributes and all un-indexed attributes we use the ndn index
        if ( db.hasUserIndexOn( type.getOid() ) )
        {
            presenceCursor = db.getPresenceIndex().forwardCursor( type.getOid() );
            ndnCursor = null;
        }
        else
        {
            presenceCursor = null;
            ndnCursor = db.getNdnIndex().forwardCursor();
        }
    }


    public boolean available()
    {
        if ( presenceCursor != null )
        {
            return presenceCursor.available();
        }

        return available;
    }


    public void beforeValue( ID id, String value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );
        if ( presenceCursor != null )
        {
            presenceCursor.beforeValue( id, value );
            return;
        }

        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void before( IndexEntry<String, ServerEntry, ID> element ) throws Exception
    {
        checkNotClosed( "before()" );
        if ( presenceCursor != null )
        {
            presenceCursor.before( element );
            return;
        }

        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void afterValue( ID id, String value ) throws Exception
    {
        checkNotClosed( "afterValue()" );
        if ( presenceCursor != null )
        {
            presenceCursor.afterValue( id, value );
            return;
        }

        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void after( IndexEntry<String, ServerEntry, ID> element ) throws Exception
    {
        checkNotClosed( "after()" );
        if ( presenceCursor != null )
        {
            presenceCursor.after( element );
            return;
        }

        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        if ( presenceCursor != null )
        {
            presenceCursor.beforeFirst();
            return;
        }

        ndnCursor.beforeFirst();
        available = false;
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        if ( presenceCursor != null )
        {
            presenceCursor.afterLast();
            return;
        }

        ndnCursor.afterLast();
        available = false;
    }


    public boolean first() throws Exception
    {
        checkNotClosed( "first()" );
        if ( presenceCursor != null )
        {
            return presenceCursor.first();
        }

        beforeFirst();
        return next();
    }


    public boolean last() throws Exception
    {
        checkNotClosed( "last()" );
        if ( presenceCursor != null )
        {
            return presenceCursor.last();
        }

        afterLast();
        return previous();
    }


    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        if ( presenceCursor != null )
        {
            return presenceCursor.previous();
        }

        while ( ndnCursor.previous() )
        {
            checkNotClosed( "previous()" );
            IndexEntry<?, ServerEntry, ID> candidate = ndnCursor.get();
            if ( presenceEvaluator.evaluate( candidate ) )
            {
                return available = true;
            }
        }

        return available = false;
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        if ( presenceCursor != null )
        {
            return presenceCursor.next();
        }

        while ( ndnCursor.next() )
        {
            checkNotClosed( "next()" );
            IndexEntry<?, ServerEntry, ID> candidate = ndnCursor.get();
            if ( presenceEvaluator.evaluate( candidate ) )
            {
                return available = true;
            }
        }

        return available = false;
    }


    public IndexEntry<String, ServerEntry, ID> get() throws Exception
    {
        checkNotClosed( "get()" );
        if ( presenceCursor != null )
        {
            if ( presenceCursor.available() )
            {
                return presenceCursor.get();
            }

            throw new InvalidCursorPositionException( I18n.err( I18n.ERR_708 ) );
        }

        if ( available )
        {
            /*
             * The value of NDN indices is the normalized dn and we want the
             * value to be the value of the attribute in question.  So we will
             * set that accordingly here.
             */
            IndexEntry<String, ServerEntry, ID> indexEntry = ndnCursor.get();
            indexEntry.setValue( presenceEvaluator.getAttributeType().getOid() );
            return indexEntry;
        }

        throw new InvalidCursorPositionException( I18n.err( I18n.ERR_708 ) );
    }


    public boolean isElementReused()
    {
        if ( presenceCursor != null )
        {
            return presenceCursor.isElementReused();
        }

        return ndnCursor.isElementReused();
    }


    public void close() throws Exception
    {
        super.close();

        if ( presenceCursor != null )
        {
            presenceCursor.close();
        }
        else
        {
            ndnCursor.close();
        }
    }
}
