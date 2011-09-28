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
package org.apache.directory.server.core.journal;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.shared.ldap.ldif.LdifEntry;


/**
 * A facade for the Journal subsystem.
 *
 * @org.apache.xbean.XBean
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public interface Journal
{
    /**
     * Checks whether or not the Journal has been enabled.
     *
     * @return true if the Journal is logging changes, false otherwise
     */
    boolean isEnabled();


    /**
     * Enable or disable the Journal service
     * @param enabled true to enable the service, false to disable it
     */
    void setEnabled( boolean enabled );
    

    /**
     * @return The underlying storage
     */
    JournalStore getJournalStore();


    /**
     * Set the underlying storage
     * @param store The storage
     */
    void setJournalStore( JournalStore store );


    /**
     * Records a change as an LDIF entry.
     *
     * @param principal the authorized LDAP principal triggering the change
     * @param revision the operation revision
     * @param forward LDIF of the change going to the next state
     * @throws Exception if there are problems logging the change
     */
    void log( LdapPrincipal principal, long revision, LdifEntry entry ) throws Exception;

    
    /**
     * Records a ack for a change
     *
     * @param revision The change revision which is acked
     */
    void ack( long revision );

    
    /**
     * Records a nack for a change
     *
     * @param revision The change revision which is acked
     */
    void nack( long revision );

    
    /**
     * Initialize the Journal.
     * 
     * @param service The associated DirectoryService
     * @throws Exception If something went wrong 
     */
    void init( DirectoryService service ) throws Exception;

    
    /**
     * Destroy the journal service
     * @throws Exception If something went wrong
     */
    void destroy() throws Exception;


    /**
     * @return the rotation
     */
    int getRotation();


    /**
     * @param rotation the rotation to set
     */
    void setRotation( int rotation );
}
