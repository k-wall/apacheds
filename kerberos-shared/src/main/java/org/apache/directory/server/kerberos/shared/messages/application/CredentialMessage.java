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
package org.apache.directory.server.kerberos.shared.messages.application;


import org.apache.directory.server.kerberos.shared.KerberosMessageType;
import org.apache.directory.server.kerberos.shared.messages.KerberosMessage;
import org.apache.directory.server.kerberos.shared.messages.components.Ticket;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedData;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 589780 $, $Date: 2007-10-29 19:14:59 +0100 (Lun, 29 oct 2007) $
 */
public class CredentialMessage extends KerberosMessage
{
    private EncryptedData encPart;
    private Ticket[] tickets;


    /**
     * Creates a new instance of CredentialMessage.
     *
     * @param encPart
     * @param tickets
     */
    public CredentialMessage( EncryptedData encPart, Ticket[] tickets )
    {
        super( KerberosMessageType.KRB_CRED );
        this.encPart = encPart;
        this.tickets = tickets;
    }


    /**
     * Returns the {@link EncryptedData}.
     *
     * @return The {@link EncryptedData}.
     */
    public EncryptedData getEncPart()
    {
        return encPart;
    }


    /**
     * Returns an array of {@link Ticket}s.
     *
     * @return The array of {@link Ticket}s.
     */
    public Ticket[] getTickets()
    {
        return tickets;
    }
}
