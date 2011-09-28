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
package org.apache.directory.server.changepw.value;


import org.apache.directory.server.kerberos.shared.messages.value.PrincipalName;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 540371 $, $Date: 2007-05-22 02:00:43 +0200 (Mar, 22 mai 2007) $
 */
public class ChangePasswordData
{
    private byte[] password;
    private PrincipalName principalName;
    private String realm;


    /**
     * Creates a new instance of ChangePasswordData.
     *
     * @param password
     * @param principalName
     * @param realm
     */
    public ChangePasswordData( byte[] password, PrincipalName principalName, String realm )
    {
        this.password = password;
        this.principalName = principalName;
        this.realm = realm;
    }


    /**
     * Returns the password as bytes.
     *
     * @return The password as bytes.
     */
    public byte[] getPassword()
    {
        return password;
    }


    /**
     * Returns the principal name.
     *
     * @return The principal name.
     */
    public PrincipalName getPrincipalName()
    {
        return principalName;
    }


    /**
     * Returns the realm.
     *
     * @return The realm.
     */
    public String getRealm()
    {
        return realm;
    }
}
