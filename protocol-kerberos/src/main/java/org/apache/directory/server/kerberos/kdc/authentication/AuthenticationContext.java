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
package org.apache.directory.server.kerberos.kdc.authentication;


import org.apache.directory.server.kerberos.kdc.KdcContext;
import org.apache.directory.server.kerberos.shared.messages.components.Ticket;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.replay.ReplayCache;
import org.apache.directory.server.kerberos.shared.store.PrincipalStoreEntry;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 642496 $, $Date: 2008-03-29 04:09:22 +0100 (Sam, 29 mar 2008) $
 */
public class AuthenticationContext extends KdcContext
{
    private static final long serialVersionUID = -2249170923251265359L;

    //private Map checksumEngines = new HashMap();

    private Ticket ticket;
    private EncryptionKey clientKey;
    private ReplayCache replayCache;

    private PrincipalStoreEntry clientEntry;
    private PrincipalStoreEntry serverEntry;

    private boolean isPreAuthenticated;


    /**
     * @return Returns the serverEntry.
     */
    public PrincipalStoreEntry getServerEntry()
    {
        return serverEntry;
    }


    /**
     * @param serverEntry The serverEntry to set.
     */
    public void setServerEntry( PrincipalStoreEntry serverEntry )
    {
        this.serverEntry = serverEntry;
    }


    /**
     * @return Returns the clientEntry.
     */
    public PrincipalStoreEntry getClientEntry()
    {
        return clientEntry;
    }


    /**
     * @param clientEntry The clientEntry to set.
     */
    public void setClientEntry( PrincipalStoreEntry clientEntry )
    {
        this.clientEntry = clientEntry;
    }


    /**
     * @return Returns the checksumEngines.
     *
    public Map getChecksumEngines()
    {
        return checksumEngines;
    }
    */

    /**
     * @param checksumEngines The checksumEngines to set.
     *
    public void setChecksumEngines( Map checksumEngines )
    {
        this.checksumEngines = checksumEngines;
    }
    */


    /**
     * @return Returns the replayCache.
     */
    public ReplayCache getReplayCache()
    {
        return replayCache;
    }


    /**
     * @param replayCache The replayCache to set.
     */
    public void setReplayCache( ReplayCache replayCache )
    {
        this.replayCache = replayCache;
    }


    /**
     * @return Returns the clientKey.
     */
    public EncryptionKey getClientKey()
    {
        return clientKey;
    }


    /**
     * @param clientKey The clientKey to set.
     */
    public void setClientKey( EncryptionKey clientKey )
    {
        this.clientKey = clientKey;
    }


    /**
     * @return Returns the ticket.
     */
    public Ticket getTicket()
    {
        return ticket;
    }


    /**
     * @param ticket The ticket to set.
     */
    public void setTicket( Ticket ticket )
    {
        this.ticket = ticket;
    }


    /**
     * @return true if the client used pre-authentication.
     */
    public boolean isPreAuthenticated()
    {
        return isPreAuthenticated;
    }


    /**
     * @param isPreAuthenticated Whether the client used pre-authentication.
     */
    public void setPreAuthenticated( boolean isPreAuthenticated )
    {
        this.isPreAuthenticated = isPreAuthenticated;
    }
}
