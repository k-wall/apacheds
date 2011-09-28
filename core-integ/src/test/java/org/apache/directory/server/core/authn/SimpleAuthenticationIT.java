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
package org.apache.directory.server.core.authn;


import static org.apache.directory.server.core.integ.IntegrationUtils.apply;
import static org.apache.directory.server.core.integ.IntegrationUtils.getUserAddLdif;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.message.ModifyRequest;
import org.apache.directory.ldap.client.api.message.SearchResultEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.core.integ.IntegrationUtils;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.apache.directory.shared.ldap.util.StringTools;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * A set of simple tests to make sure simple authentication is working as it
 * should.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 925248 $
 */
@RunWith(FrameworkRunner.class)
@CreateDS( name="SimpleAuthenticationIT-DS" )
public class SimpleAuthenticationIT extends AbstractLdapTestUnit
{
    /**
     * Checks all attributes of the admin account entry minus the userPassword
     * attribute.
     *
     * @param entry the entries attributes
     */
    protected void performAdminAccountChecks( Entry entry )
    {
        assertTrue( entry.get( "objectClass" ).contains( "top" ) );
        assertTrue( entry.get( "objectClass" ).contains( "person" ) );
        assertTrue( entry.get( "objectClass" ).contains( "organizationalPerson" ) );
        assertTrue( entry.get( "objectClass" ).contains( "inetOrgPerson" ) );
        assertTrue( entry.get( "displayName" ).contains( "Directory Superuser" ) );
    }


    @After
    public void closeConnections()
    {
        IntegrationUtils.closeConections();
    }
    
    
    /**
     * Check the creation of the admin account and persistence across restarts.
     *
     * @throws Exception if there are failures
     */
    @Test
    public void testAdminAccountCreation() throws Exception
    {
        String userDn = "uid=admin,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "secret" );

        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        performAdminAccountChecks( entry );
        assertTrue( ArrayUtils.isEquals( entry.get( "userPassword" ).get().getBytes(), StringTools
            .getBytesUtf8( "secret" ) ) );
        connection.close();

        ldapServer.stop();
        ldapServer.start();

        connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        performAdminAccountChecks( entry );
        assertTrue( ArrayUtils.isEquals( entry.get( "userPassword" ).get().getBytes(), StringTools
            .getBytesUtf8( "secret" ) ) );
        connection.close();
    }


    @Test
    public void test3UseAkarasulu() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        EntryAttribute ou = entry.get( "ou" );
        assertTrue( ou.contains( "Engineering" ) );
        assertTrue( ou.contains( "People" ) );

        EntryAttribute objectClass = entry.get( "objectClass" );
        assertTrue( objectClass.contains( "top" ) );
        assertTrue( objectClass.contains( "person" ) );
        assertTrue( objectClass.contains( "organizationalPerson" ) );
        assertTrue( objectClass.contains( "inetOrgPerson" ) );

        assertTrue( entry.get( "telephonenumber" ).contains( "+1 408 555 4798" ) );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
        assertTrue( entry.get( "givenname" ).contains( "Alex" ) );
        assertTrue( entry.get( "mail" ).contains( "akarasulu@apache.org" ) );
        assertTrue( entry.get( "l" ).contains( "Bogusville" ) );
        assertTrue( entry.get( "sn" ).contains( "Karasulu" ) );
        assertTrue( entry.get( "cn" ).contains( "Alex Karasulu" ) );
        assertTrue( entry.get( "facsimiletelephonenumber" ).contains( "+1 408 555 9751" ) );
        assertTrue( entry.get( "roomnumber" ).contains( "4612" ) );
        connection.close();
    }


    /**
     * Tests to make sure we can authenticate after the database has already
     * been started by the admin user when simple authentication is in effect.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void test8PassPrincAuthTypeSimple() throws Exception
    {
        String userDn = "uid=admin,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "secret" );
        assertTrue( connection.isAuthenticated() );
        connection.close();
    }


    /**
     * Checks to see if we can authenticate as a test user after the admin fires
     * up and builds the the system database.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void test10TestNonAdminUser() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );
        assertTrue( connection.isAuthenticated() );
        connection.close();
    }


    @Test
    public void test11InvalidateCredentialCache() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";

        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        EntryAttribute ou = entry.get( "ou" );
        assertTrue( ou.contains( "Engineering" ) );
        assertTrue( ou.contains( "People" ) );

        EntryAttribute objectClass = entry.get( "objectClass" );
        assertTrue( objectClass.contains( "top" ) );
        assertTrue( objectClass.contains( "person" ) );
        assertTrue( objectClass.contains( "organizationalPerson" ) );
        assertTrue( objectClass.contains( "inetOrgPerson" ) );

        assertTrue( entry.get( "telephonenumber" ).contains( "+1 408 555 4798" ) );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
        assertTrue( entry.get( "givenname" ).contains( "Alex" ) );
        assertTrue( entry.get( "mail" ).contains( "akarasulu@apache.org" ) );
        assertTrue( entry.get( "l" ).contains( "Bogusville" ) );
        assertTrue( entry.get( "sn" ).contains( "Karasulu" ) );
        assertTrue( entry.get( "cn" ).contains( "Alex Karasulu" ) );
        assertTrue( entry.get( "facsimiletelephonenumber" ).contains( "+1 408 555 9751" ) );
        assertTrue( entry.get( "roomnumber" ).contains( "4612" ) );

        // now modify the password for akarasulu
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "newpwd" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );

        // close and try again now with new password (should fail)
        connection.close();
        connection.bind( userDn, "newpwd" );

        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        ou = entry.get( "ou" );
        assertTrue( ou.contains( "Engineering" ) );
        assertTrue( ou.contains( "People" ) );

        objectClass = entry.get( "objectClass" );
        assertTrue( objectClass.contains( "top" ) );
        assertTrue( objectClass.contains( "person" ) );
        assertTrue( objectClass.contains( "organizationalPerson" ) );
        assertTrue( objectClass.contains( "inetOrgPerson" ) );

        assertTrue( entry.get( "telephonenumber" ).contains( "+1 408 555 4798" ) );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
        assertTrue( entry.get( "givenname" ).contains( "Alex" ) );
        assertTrue( entry.get( "mail" ).contains( "akarasulu@apache.org" ) );
        assertTrue( entry.get( "l" ).contains( "Bogusville" ) );
        assertTrue( entry.get( "sn" ).contains( "Karasulu" ) );
        assertTrue( entry.get( "cn" ).contains( "Alex Karasulu" ) );
        assertTrue( entry.get( "facsimiletelephonenumber" ).contains( "+1 408 555 9751" ) );
        assertTrue( entry.get( "roomnumber" ).contains( "4612" ) );
    }


    @Test
    public void testSHA() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        // Check that we can get the attributes

        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // now modify the password for akarasulu : 'secret', encrypted using SHA
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "{SHA}5en6G6MezRroT3XKqkdPOmY/BfQ=" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();

        // try again now with new password (should be successfull)
        connection.bind( userDn, "secret" );
        assertTrue( connection.isAuthenticated() );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // close and try again now with new password, to check that the
        // cache is updated (should be successfull)
        connection.close();
        connection.bind( userDn, "secret" );
        assertTrue( connection.isAuthenticated() );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
    }


    @Test
    public void testSSHA() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        // Check that we can get the attributes
        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // now modify the password for akarasulu : 'secret', encrypted using SHA
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "{SSHA}mjVVxasFkk59wMW4L1Ldt+YCblfhULHs03WW7g==" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();

        // try again now with new password (should be successfull)
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // close and try again now with new password, to check that the
        // cache is updated (should be successfull)
        connection.close();
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
    }


    @Test
    public void testSSHA4BytesSalt() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        // Check that we can get the attributes
        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // now modify the password for akarasulu : 'test123', encrypted using SHA with a 4 bytes salt
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "{SSHA}0TT388zsWzHKtMEpIU/8/W68egchNEWp" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();

        // try again now with new password (should be successful)
        connection.bind( userDn, "test123" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // close and try again now with new password, to check that the
        // cache is updated (should be successfull)
        connection.close();
        connection.bind( userDn, "test123" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
    }


    @Test
    public void testMD5() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        // Check that we can get the attributes
        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // now modify the password for akarasulu : 'secret', encrypted using MD5
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "{MD5}Xr4ilOzQ4PCOq3aQ0qbuaQ==" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();

        // try again now with new password (should be successfull)
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // try again now with new password, to check that the
        // cache is updated (should be successfull)

        connection.close();
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
    }


    @Test
    public void testSMD5() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        // Check that we can get the attributes
        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // now modify the password for akarasulu : 'secret', encrypted using SMD5
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "{SMD5}tQ9wo/VBuKsqBtylMMCcORbnYOJFMyDJ" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();

        // try again now with new password (should be successful)
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // try again now with new password, to check that the
        // cache is updated (should be successfull)
        connection.close();
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
    }


    @Test
    public void testCRYPT() throws Exception
    {
        apply( service, getUserAddLdif() );
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );

        // Check that we can get the attributes
        Entry entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // now modify the password for akarasulu : 'secret', encrypted using CRYPT
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "{crypt}qFkH8Z1woBlXw" );
        connection.modify( modReq );

        // close and try with old password (should fail)
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();

        // try again now with new password (should be successfull)
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );

        // try again now with new password, to check that the
        // cache is updated (should be successfull)
        connection.bind( userDn, "secret" );
        entry = ( ( SearchResultEntry ) connection.lookup( userDn ) ).getEntry();
        assertNotNull( entry );
        assertTrue( entry.get( "uid" ).contains( "akarasulu" ) );
    }


    @Test
    public void testInvalidateCredentialCacheForUpdatingAnotherUsersPassword() throws Exception
    {
        apply( service, getUserAddLdif() );

        // bind as akarasulu
        String userDn = "uid=akarasulu,ou=users,ou=system";
        LdapConnection connection = new LdapConnection( "localhost", ldapServer.getPort() );
        connection.bind( userDn, "test" );
        connection.close();

        // bind as admin
        String adminUserDn = "uid=admin,ou=system";
        connection.bind( adminUserDn, "secret" );

        // now modify the password for akarasulu (while we're admin)
        ModifyRequest modReq = new ModifyRequest( new DN( userDn ) );
        modReq.replace( "userPassword", "newpwd" );
        connection.modify( modReq );
        connection.close();

        connection.bind( userDn, "test" );
        assertFalse( connection.isAuthenticated() );
        connection.close();
    }
}
