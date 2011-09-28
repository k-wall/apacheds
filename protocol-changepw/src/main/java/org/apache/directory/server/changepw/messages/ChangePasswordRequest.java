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
package org.apache.directory.server.changepw.messages;


import org.apache.directory.server.kerberos.shared.messages.ApplicationRequest;
import org.apache.directory.server.kerberos.shared.messages.application.PrivateMessage;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 557127 $, $Date: 2007-07-18 05:02:51 +0200 (Mer, 18 jul 2007) $
 */
public class ChangePasswordRequest extends AbstractPasswordMessage
{
    private ApplicationRequest authHeader;
    private PrivateMessage privateMessage;


    /**
     * Creates a new instance of ChangePasswordRequest.
     *
     * @param versionNumber
     * @param authHeader
     * @param privateMessage
     */
    public ChangePasswordRequest( short versionNumber, ApplicationRequest authHeader, PrivateMessage privateMessage )
    {
        super( versionNumber );

        this.authHeader = authHeader;
        this.privateMessage = privateMessage;
    }


    /**
     * Returns the {@link ApplicationRequest}.
     *
     * @return The {@link ApplicationRequest}.
     */
    public ApplicationRequest getAuthHeader()
    {
        return authHeader;
    }


    /**
     * Returns the {@link PrivateMessage}.
     *
     * @return The {@link PrivateMessage}.
     */
    public PrivateMessage getPrivateMessage()
    {
        return privateMessage;
    }
}
