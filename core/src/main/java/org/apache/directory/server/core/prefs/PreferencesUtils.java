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
package org.apache.directory.server.core.prefs;

 
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.name.DN;


/**
 * Document this class.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 923747 $
 */
class PreferencesUtils
{
    /**
     * Translates an absolute system preferences node name into the distinguished
     * name of the entry corresponding to the preferences node.
     *
     * @param absPrefPath the absolute path to the system preferences node
     * @return the distinguished name of the entry representing the system preferences node
     * @throws LdapInvalidDnException if there are namespace problems while translating the path
     */
    public static DN toSysDn( String absPrefPath ) throws LdapInvalidDnException
    {
        DN dn = new DN( ServerDNConstants.SYSPREFROOT_SYSTEM_DN );

        String[] comps = absPrefPath.split( "/" );

        for ( int ii = 0; ii < comps.length; ii++ )
        {
            if ( comps[ii] != null && !comps[ii].trim().equals( "" ) )
            {
                dn.add( "prefNodeName=" + comps[ii] );
            }
        }

        return dn;
    }
}
