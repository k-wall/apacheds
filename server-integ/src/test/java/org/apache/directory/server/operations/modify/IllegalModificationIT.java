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
package org.apache.directory.server.operations.modify;


import static org.apache.directory.server.integ.ServerIntegrationUtils.getClientApiConnection;
import static org.apache.directory.server.integ.ServerIntegrationUtils.getWiredConnection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import netscape.ldap.LDAPAttribute;
import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPEntry;
import netscape.ldap.LDAPException;
import netscape.ldap.LDAPModification;

import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.message.ModifyRequest;
import org.apache.directory.ldap.client.api.message.ModifyResponse;
import org.apache.directory.ldap.client.api.message.SearchResultEntry;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.Test;
import org.junit.runner.RunWith;


/** 
 * A test taken from DIRSERVER-630: If one tries to add an attribute to an 
 * entry, and does not provide a value, it is assumed that the server does 
 * not modify the entry. We have a situation here using Sun ONE Directory 
 * SDK for Java, where adding a description attribute without value to a 
 * person entry like this,
 * <code>
 * dn: cn=Kate Bush,dc=example,dc=com
 * objectclass: person
 * objectclass: top
 * sn: Bush
 * cn: Kate Bush
 * </code> 
 * does not fail (modify call does not result in an exception). Instead, a 
 * description attribute is created within the entry. At least the new 
 * attribute is readable with Netscape SDK (it is not visible to most UIs, 
 * because it is invalid ...). 
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: $
 */
@RunWith ( FrameworkRunner.class ) 
//@CreateDS( name="IllegalModificationIT-class", enableChangeLog=false )
@CreateLdapServer ( 
    transports = 
    {
        @CreateTransport( protocol = "LDAP" )
    })
@ApplyLdifs( {
    // Entry # 1
    "dn: cn=Kate Bush,ou=system",
    "objectClass: person",
    "objectClass: top",
    "cn: Kate Bush",
    "sn: Bush" 
    }
)
public class IllegalModificationIT extends AbstractLdapTestUnit
{
    private static final String DN = "cn=Kate Bush,ou=system";

    @Test
    public void testIllegalModification() throws Exception
    {
        LdapConnection con = getClientApiConnection( ldapServer );

        ModifyRequest modReq = new ModifyRequest( new DN( DN ) );
        modReq.add( "description", "" );

        ModifyResponse resp = con.modify( modReq );
        assertEquals( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, resp.getLdapResult().getResultCode() );

        // Check whether entry is unmodified, i.e. no description
        Entry entry = ( ( SearchResultEntry )con.lookup( DN ) ).getEntry();
        assertEquals( "description exists?", null, entry.get( "description" ) );
    }
    
    
    @Test
    public void testIllegalModification2() throws Exception
    {
        LDAPConnection con = getWiredConnection( ldapServer );

        // first a valid attribute
        LDAPAttribute attr = new LDAPAttribute( "description", "The description" );
        LDAPModification mod = new LDAPModification( LDAPModification.ADD, attr );
        // then an invalid one without any value
        attr = new LDAPAttribute( "displayName" );
        LDAPModification mod2 = new LDAPModification( LDAPModification.ADD, attr );

        try
        {
            con.modify( "cn=Kate Bush,ou=system", new LDAPModification[] { mod, mod2 } );
            fail( "error expected due to empty attribute value" );
        }
        catch ( LDAPException e )
        {
            // expected
        }

        // Check whether entry is unmodified, i.e. no displayName
        LDAPEntry entry = con.read( DN );
        assertEquals( "displayName exists?", null, entry.getAttribute( "displayName" ) );
    }
}
