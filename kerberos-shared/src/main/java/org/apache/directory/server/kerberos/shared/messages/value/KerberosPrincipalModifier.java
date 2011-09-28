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
package org.apache.directory.server.kerberos.shared.messages.value;


import javax.security.auth.kerberos.KerberosPrincipal;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587146 $, $Date: 2007-10-22 18:28:37 +0200 (Lun, 22 oct 2007) $
 */
public class KerberosPrincipalModifier
{
    private static final String REALM_SEPARATOR = "@";

    PrincipalName nameComponent;
    String realm;


    /**
     * Returns the {@link KerberosPrincipal}.
     *
     * @return The {@link KerberosPrincipal}.
     */
    public KerberosPrincipal getKerberosPrincipal()
    {
        if ( nameComponent != null )
        {
            StringBuffer sb = new StringBuffer();
            sb.append( nameComponent.getNameString() );

            if ( realm != null )
            {
                sb.append( REALM_SEPARATOR );
                sb.append( realm );
            }

            return new KerberosPrincipal( sb.toString(), nameComponent.getNameType().getOrdinal() );
        }

        return null;
    }


    /**
     * Sets the {@link PrincipalName}.
     *
     * @param principalName
     */
    public void setPrincipalName( PrincipalName principalName )
    {
        nameComponent = principalName;
    }


    /**
     * Sets the realm.
     *
     * @param realm
     */
    public void setRealm( String realm )
    {
        this.realm = realm;
    }
}
