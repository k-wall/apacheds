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
package org.apache.directory.server.ldap.handlers;


import org.apache.directory.server.core.event.DirectoryListener;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.shared.ldap.exception.OperationAbandonedException;
import org.apache.directory.shared.ldap.message.AbandonListener;
import org.apache.directory.shared.ldap.message.internal.InternalAbandonableRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An AbandonListener implementation which closes an associated cursor or 
 * removes a DirectoryListener.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class SearchAbandonListener implements AbandonListener
{
    private static final Logger LOG = LoggerFactory.getLogger( SearchAbandonListener.class );
    private final LdapServer ldapServer;
    private EntryFilteringCursor cursor;
    private DirectoryListener listener;
    
    
    public SearchAbandonListener( LdapServer ldapServer, EntryFilteringCursor cursor, DirectoryListener listener )
    {
        if ( ldapServer == null )
        {
            throw new NullPointerException( "ldapServer" );
        }
        
        this.ldapServer = ldapServer;
        this.cursor = cursor;
        this.listener = listener;
    }
    
    
    public SearchAbandonListener( LdapServer ldapServer, DirectoryListener listener )
    {
        this ( ldapServer, null, listener );
    }
    
    
    public SearchAbandonListener( LdapServer ldapServer, EntryFilteringCursor cursor )
    {
        this ( ldapServer, cursor, null );
    }
    
    
    public void requestAbandoned( InternalAbandonableRequest req )
    {
        if ( listener != null )
        {
            ldapServer.getDirectoryService().getEventService().removeListener( listener );
        }

        try
        {
            if ( cursor != null )
            {
                /*
                 * When this method is called due to an abandon request it 
                 * will close the cursor but other threads processing the 
                 * search will get an OperationAbandonedException which as
                 * seen below will make sure the proper handling is 
                 * performed.
                 */
                cursor.close( new OperationAbandonedException() );
            }
        }
        catch ( Exception e )
        {
            LOG.error( I18n.err( I18n.ERR_166, req.getMessageId() ), e );
        }
    }
}


