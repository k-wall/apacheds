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


import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.AbstractIndexCursor;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.shared.ldap.cursor.InvalidCursorPositionException;
import org.apache.directory.shared.ldap.entry.ServerEntry;


/**
 * A Cursor traversing candidates matching a Substring assertion expression.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SubstringCursor<ID> extends AbstractIndexCursor<String, ServerEntry, ID>
{
    private static final String UNSUPPORTED_MSG = I18n.err( I18n.ERR_725 );
    private final boolean hasIndex;
    private final IndexCursor<String, ServerEntry, ID> wrapped;
    private final SubstringEvaluator<ID> evaluator;
    private final ForwardIndexEntry<String, ServerEntry, ID> indexEntry = new ForwardIndexEntry<String, ServerEntry, ID>();
    private boolean available = false;


    @SuppressWarnings("unchecked")
    public SubstringCursor( Store<ServerEntry, ID> db, final SubstringEvaluator<ID> substringEvaluator )
        throws Exception
    {
        evaluator = substringEvaluator;
        hasIndex = db.hasIndexOn( evaluator.getExpression().getAttribute() );

        if ( hasIndex )
        {
            wrapped = ( ( Index<String, ServerEntry, ID> ) db.getIndex( evaluator.getExpression().getAttribute() ) )
                .forwardCursor();
        }
        else
        {
            /*
             * There is no index on the attribute here.  We have no choice but
             * to perform a full table scan but need to leverage an index for the
             * wrapped Cursor.  We know that all entries are listed under
             * the ndn index and so this will enumerate over all entries.  The
             * substringEvaluator is used in an assertion to constrain the
             * result set to only those entries matching the pattern.  The
             * substringEvaluator handles all the details of normalization and
             * knows to use it, when it itself detects the lack of an index on
             * the node's attribute.
             */
            wrapped = db.getNdnIndex().forwardCursor();
        }
    }


    public boolean available()
    {
        return available;
    }


    public void beforeValue( ID id, String value ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void afterValue( ID id, String value ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void before( IndexEntry<String, ServerEntry, ID> element ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void after( IndexEntry<String, ServerEntry, ID> element ) throws Exception
    {
        throw new UnsupportedOperationException( UNSUPPORTED_MSG );
    }


    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        if ( evaluator.getExpression().getInitial() != null && hasIndex )
        {
            ForwardIndexEntry<String, ServerEntry, ID> indexEntry = new ForwardIndexEntry<String, ServerEntry, ID>();
            indexEntry.setValue( evaluator.getExpression().getInitial() );
            wrapped.before( indexEntry );
        }
        else
        {
            wrapped.beforeFirst();
        }

        clear();
    }


    private void clear()
    {
        available = false;
        indexEntry.setObject( null );
        indexEntry.setId( null );
        indexEntry.setValue( null );
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );

        // to keep the cursor always *after* the last matched tuple
        // This fixes an issue if the last matched tuple is also the last record present in the 
        // index. In this case the wrapped cursor is positioning on the last tuple instead of positioning after that
        wrapped.afterLast();
        clear();
    }


    public boolean first() throws Exception
    {
        beforeFirst();
        return next();
    }


    private boolean evaluateCandidate( IndexEntry<String, ServerEntry, ID> indexEntry ) throws Exception
    {
        if ( hasIndex )
        {
            return evaluator.getPattern().matcher( indexEntry.getValue() ).matches();
        }
        else
        {
            return evaluator.evaluate( indexEntry );
        }
    }


    public boolean last() throws Exception
    {
        afterLast();
        return previous();
    }


    public boolean previous() throws Exception
    {
        while ( wrapped.previous() )
        {
            checkNotClosed( "previous()" );
            IndexEntry<String, ServerEntry, ID> entry = wrapped.get();
            if ( evaluateCandidate( entry ) )
            {
                available = true;
                this.indexEntry.setId( entry.getId() );
                this.indexEntry.setValue( entry.getValue() );
                this.indexEntry.setObject( entry.getObject() );
                return true;
            }
        }

        clear();
        return false;
    }


    public boolean next() throws Exception
    {
        while ( wrapped.next() )
        {
            checkNotClosed( "next()" );
            IndexEntry<String, ServerEntry, ID> entry = wrapped.get();
            if ( evaluateCandidate( entry ) )
            {
                available = true;
                this.indexEntry.setId( entry.getId() );
                this.indexEntry.setValue( entry.getValue() );
                this.indexEntry.setObject( entry.getObject() );
                return true;
            }
        }

        clear();
        return false;
    }


    public IndexEntry<String, ServerEntry, ID> get() throws Exception
    {
        checkNotClosed( "get()" );
        if ( available )
        {
            return indexEntry;
        }

        throw new InvalidCursorPositionException( I18n.err( I18n.ERR_708 ) );
    }


    public boolean isElementReused()
    {
        return wrapped.isElementReused();
    }


    public void close() throws Exception
    {
        super.close();
        wrapped.close();
        clear();
    }
}
