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
package org.apache.directory.server.xdbm.search.impl;


import org.apache.directory.server.xdbm.AbstractIndexCursor;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.shared.ldap.entry.ServerEntry;


/**
 * A Cursor over all entries in a partition which returns IndexEntries.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class AllEntriesCursor<ID> extends AbstractIndexCursor<ID, ServerEntry, ID>
{
    private IndexEntry<ID, ServerEntry, ID> indexEntry = new ForwardIndexEntry<ID, ServerEntry, ID>();
    private final IndexCursor<String, ServerEntry, ID> wrapped;


    public AllEntriesCursor( Store<ServerEntry, ID> db ) throws Exception
    {
        // Get a reverse cursor because we want to sort by ID
        wrapped = db.getNdnIndex().reverseCursor();
    }


    /* 
     * @see org.apache.directory.server.xdbm.IndexCursor#afterValue(Long, Object)
     */
    public void afterValue( ID key, ID value ) throws Exception
    {
        checkNotClosed( "afterValue()" );
        wrapped.afterValue( key, null );
    }


    /* 
     * @see org.apache.directory.server.xdbm.IndexCursor#beforeValue(java.lang.Long, java.lang.Object)
     */
    public void beforeValue( ID id, ID value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );
        wrapped.beforeValue( id, null );
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#after(java.lang.Object)
     */
    public void after( IndexEntry<ID, ServerEntry, ID> indexEntry ) throws Exception
    {
        checkNotClosed( "after()" );
        wrapped.afterValue( indexEntry.getId(), null );
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#afterLast()
     */
    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        wrapped.afterLast();
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#available()
     */
    public boolean available()
    {
        return wrapped.available();
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#before(java.lang.Object)
     */
    public void before( IndexEntry<ID, ServerEntry, ID> indexEntry ) throws Exception
    {
        checkNotClosed( "before()" );
        wrapped.beforeValue( indexEntry.getId(), null );
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#beforeFirst()
     */
    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        wrapped.beforeFirst();
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#first()
     */
    public boolean first() throws Exception
    {
        checkNotClosed( "first()" );
        return wrapped.first();
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#get()
     */
    public IndexEntry<ID, ServerEntry, ID> get() throws Exception
    {
        checkNotClosed( "get()" );
        IndexEntry<String, ServerEntry, ID> wrappedEntry = wrapped.get();
        indexEntry.setId( wrappedEntry.getId() );
        indexEntry.setValue( wrappedEntry.getId() );
        indexEntry.setObject( wrappedEntry.getObject() );
        return indexEntry;
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#isElementReused()
     */
    public boolean isElementReused()
    {
        return true;
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#last()
     */
    public boolean last() throws Exception
    {
        checkNotClosed( "last()" );
        return wrapped.last();
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#next()
     */
    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        return wrapped.next();
    }


    /* 
     * @see org.apache.directory.server.core.cursor.Cursor#previous()
     */
    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        return wrapped.previous();
    }
}
