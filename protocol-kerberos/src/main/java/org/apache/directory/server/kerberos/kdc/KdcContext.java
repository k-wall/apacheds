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
package org.apache.directory.server.kerberos.kdc;


import java.net.InetAddress;

import org.apache.directory.server.kerberos.shared.crypto.encryption.CipherTextHandler;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.KdcRequest;
import org.apache.directory.server.kerberos.shared.messages.KerberosMessage;
import org.apache.directory.server.kerberos.shared.store.PrincipalStore;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 583938 $, $Date: 2007-10-11 21:57:20 +0200 (Jeu, 11 oct 2007) $
 */
public class KdcContext
{
    private static final long serialVersionUID = 6490030984626825108L;

    private KdcServer config;
    private PrincipalStore store;
    private KdcRequest request;
    private KerberosMessage reply;
    private InetAddress clientAddress;
    private CipherTextHandler cipherTextHandler;
    private EncryptionType encryptionType;


    /**
     * @return Returns the config.
     */
    public KdcServer getConfig()
    {
        return config;
    }


    /**
     * @param config The config to set.
     */
    public void setConfig( KdcServer config )
    {
        this.config = config;
    }


    /**
     * @return Returns the store.
     */
    public PrincipalStore getStore()
    {
        return store;
    }


    /**
     * @param store The store to set.
     */
    public void setStore( PrincipalStore store )
    {
        this.store = store;
    }


    /**
     * @return Returns the request.
     */
    public KdcRequest getRequest()
    {
        return request;
    }


    /**
     * @param request The request to set.
     */
    public void setRequest( KdcRequest request )
    {
        this.request = request;
    }


    /**
     * @return Returns the reply.
     */
    public KerberosMessage getReply()
    {
        return reply;
    }


    /**
     * @param reply The reply to set.
     */
    public void setReply( KerberosMessage reply )
    {
        this.reply = reply;
    }


    /**
     * @return Returns the clientAddress.
     */
    public InetAddress getClientAddress()
    {
        return clientAddress;
    }


    /**
     * @param clientAddress The clientAddress to set.
     */
    public void setClientAddress( InetAddress clientAddress )
    {
        this.clientAddress = clientAddress;
    }


    /**
     * @return Returns the {@link CipherTextHandler}.
     */
    public CipherTextHandler getCipherTextHandler()
    {
        return cipherTextHandler;
    }


    /**
     * @param cipherTextHandler The {@link CipherTextHandler} to set.
     */
    public void setCipherTextHandler( CipherTextHandler cipherTextHandler )
    {
        this.cipherTextHandler = cipherTextHandler;
    }


    /**
     * Returns the encryption type to use for this session.
     *
     * @return The encryption type.
     */
    public EncryptionType getEncryptionType()
    {
        return encryptionType;
    }


    /**
     * Sets the encryption type to use for this session.
     *
     * @param encryptionType The encryption type to set.
     */
    public void setEncryptionType( EncryptionType encryptionType )
    {
        this.encryptionType = encryptionType;
    }
}
