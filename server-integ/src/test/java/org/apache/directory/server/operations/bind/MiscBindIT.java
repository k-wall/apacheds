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
package org.apache.directory.server.operations.bind;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import netscape.ldap.LDAPConnection;
import netscape.ldap.LDAPException;
import netscape.ldap.LDAPSearchResults;
import netscape.ldap.LDAPUrl;

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.ldap.handlers.bind.MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.SimpleMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.cramMD5.CramMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.digestMD5.DigestMd5MechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.gssapi.GssapiMechanismHandler;
import org.apache.directory.server.ldap.handlers.bind.ntlm.NtlmMechanismHandler;
import org.apache.directory.server.ldap.handlers.extended.StoredProcedureExtendedOperationHandler;
import org.apache.directory.shared.asn1.util.Asn1StringUtils;
import org.apache.directory.shared.ldap.codec.controls.ControlImpl;
import org.apache.directory.shared.ldap.constants.SupportedSaslMechanisms;
import org.apache.directory.shared.ldap.jndi.JndiUtils;
import org.apache.directory.shared.ldap.message.control.Control;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * A set of miscellaneous tests.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 682556 $
 */
@RunWith ( FrameworkRunner.class ) 
@CreateDS( allowAnonAccess=true, name="SaslBindIT-class",
    partitions =
        {
            @CreatePartition(
                name = "example",
                suffix = "dc=aPache,dc=org",
                contextEntry = @ContextEntry( 
                    entryLdif =
                        "dn: dc=aPache,dc=org\n" +
                        "dc: aPache\n" +
                        "objectClass: top\n" +
                        "objectClass: domain\n\n" ),
                indexes = 
                {
                    @CreateIndex( attribute = "objectClass" ),
                    @CreateIndex( attribute = "dc" ),
                    @CreateIndex( attribute = "ou" )
                } )
        })
@CreateLdapServer ( 
    transports = 
    {
        @CreateTransport( protocol = "LDAP" )
    })
public class MiscBindIT extends AbstractLdapTestUnit
{
    private boolean oldAnnonymousAccess;

    @Before
    public void init() throws Exception
    {
        ldapServer.addExtendedOperationHandler( new StoredProcedureExtendedOperationHandler() );

        // Setup SASL Mechanisms
        
        Map<String, MechanismHandler> mechanismHandlerMap = new HashMap<String,MechanismHandler>();
        mechanismHandlerMap.put( SupportedSaslMechanisms.PLAIN, new SimpleMechanismHandler() );

        CramMd5MechanismHandler cramMd5MechanismHandler = new CramMd5MechanismHandler();
        mechanismHandlerMap.put( SupportedSaslMechanisms.CRAM_MD5, cramMd5MechanismHandler );

        DigestMd5MechanismHandler digestMd5MechanismHandler = new DigestMd5MechanismHandler();
        mechanismHandlerMap.put( SupportedSaslMechanisms.DIGEST_MD5, digestMd5MechanismHandler );

        GssapiMechanismHandler gssapiMechanismHandler = new GssapiMechanismHandler();
        mechanismHandlerMap.put( SupportedSaslMechanisms.GSSAPI, gssapiMechanismHandler );

        NtlmMechanismHandler ntlmMechanismHandler = new NtlmMechanismHandler();
        mechanismHandlerMap.put( SupportedSaslMechanisms.NTLM, ntlmMechanismHandler );
        mechanismHandlerMap.put( SupportedSaslMechanisms.GSS_SPNEGO, ntlmMechanismHandler );

        ldapServer.setSaslMechanismHandlers( mechanismHandlerMap );
        oldAnnonymousAccess = ldapServer.getDirectoryService().isAllowAnonymousAccess();
    }
    
    
    @After
    public void revertAnonnymous()
    {
        ldapServer.getDirectoryService().setAllowAnonymousAccess( oldAnnonymousAccess );
        ldapServer.setAllowAnonymousAccess( oldAnnonymousAccess );
    }

    
    /**
     * Test to make sure anonymous binds are disabled when going through
     * the wire protocol.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testDisableAnonymousBinds() throws Exception
    {
        ldapServer.getDirectoryService().setAllowAnonymousAccess( false );
        ldapServer.setAllowAnonymousAccess( false );
        
        // Use the SUN JNDI provider to hit server port and bind as anonymous
        InitialDirContext ic = null;
        final Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() + "/ou=system" );
        env.put( Context.SECURITY_AUTHENTICATION, "none" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        try
        {
            ic = new InitialDirContext( env );
            fail();
        }
        catch ( Exception e )
        {
            // We should get here
        }

        try
        {
            // Use the netscape API as JNDI cannot be used to do a search without
            // first binding.
            LDAPUrl url = new LDAPUrl( "localhost", ldapServer.getPort(), "ou=system", new String[]{"vendorName"}, 0, "(ObjectClass=*)" );
            LDAPSearchResults results = LDAPConnection.search( url );

            fail();
        }
        catch ( LDAPException e )
        {
            // Expected result
        }
    }


    /**
     * Test to make sure anonymous binds are allowed on the RootDSE even when disabled
     * in general when going through the wire protocol.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testEnableAnonymousBindsOnRootDSE() throws Exception
    {
        ldapServer.getDirectoryService().setAllowAnonymousAccess( true );

        // Use the SUN JNDI provider to hit server port and bind as anonymous
        Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() + "/" );
        env.put( Context.SECURITY_AUTHENTICATION, "none" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        InitialDirContext ctx = new InitialDirContext( env );
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.OBJECT_SCOPE );
        NamingEnumeration<SearchResult> list = ctx.search( "", "(objectClass=*)", cons );
        
        SearchResult result = null;
        
        if ( list.hasMore() )
        {
            result = list.next();
        }
        
        assertFalse( list.hasMore() );
        list.close();

        assertNotNull( result );
        assertEquals( "", result.getName().trim() );
    }


    /**
     * Test to make sure that if anonymous binds are allowed a user may search
     * within a a partition.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testAnonymousBindsEnabledBaseSearch() throws Exception
    {
        ldapServer.getDirectoryService().setAllowAnonymousAccess( true );

        // Use the SUN JNDI provider to hit server port and bind as anonymous
        Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() + "/" );
        env.put( Context.SECURITY_AUTHENTICATION, "none" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        InitialDirContext ctx = new InitialDirContext( env );
        SearchControls cons = new SearchControls();
        cons.setSearchScope( SearchControls.OBJECT_SCOPE );
        NamingEnumeration<SearchResult> list = ctx.search( "dc=apache,dc=org", "(objectClass=*)", cons );
        SearchResult result = null;
        
        if ( list.hasMore() )
        {
            result = list.next();
        }
        
        assertFalse( list.hasMore() );
        list.close();

        assertNotNull( result );
        assertNotNull( result.getAttributes().get( "dc" ) );
    }


    /**
     * Reproduces the problem with
     * <a href="http://issues.apache.org/jira/browse/DIREVE-239">DIREVE-239</a>.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testAdminAccessBug() throws Exception
    {
        ldapServer.getDirectoryService().setAllowAnonymousAccess( true );

        // Use the SUN JNDI provider to hit server port and bind as anonymous

        final Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() );
        env.put( "java.naming.ldap.version", "3" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );

        Attributes attributes = new BasicAttributes( true );
        Attribute objectClass = new BasicAttribute( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "organizationalUnit" );
        attributes.put( objectClass );
        attributes.put( "ou", "blah" );
        InitialDirContext ctx = new InitialDirContext( env );
        ctx.createSubcontext( "ou=blah,ou=system", attributes );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.OBJECT_SCOPE );
        controls.setReturningAttributes( new String[]
                {"+"} );
        NamingEnumeration<SearchResult> list = ctx.search( "ou=blah,ou=system", "(objectClass=*)", controls );
        SearchResult result = list.next();
        list.close();
        Attribute creatorsName = result.getAttributes().get( "creatorsName" );
        assertEquals( "", creatorsName.get() );
    }


    /**
     * Test case for <a href="http://issues.apache.org/jira/browse/DIREVE-284" where users in
     * mixed case partitions were not able to authenticate properly.  This test case creates
     * a new partition under dc=aPache,dc=org, it then creates the example user in the JIRA
     * issue and attempts to authenticate as that user.
     *
     * @throws Exception if the user cannot authenticate or test fails
     */
    @Test
    public void testUserAuthOnMixedCaseSuffix() throws Exception
    {
        ldapServer.getDirectoryService().setAllowAnonymousAccess( true );

        Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() + "/dc=aPache,dc=org" );
        env.put( "java.naming.ldap.version", "3" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        InitialDirContext ctx = new InitialDirContext( env );
        Attributes attrs = ctx.getAttributes( "" );
        assertTrue( attrs.get( "dc" ).get().equals( "aPache" ) );

        Attributes user = new BasicAttributes( "cn", "Kate Bush", true );
        Attribute oc = new BasicAttribute( "objectClass" );
        oc.add( "top" );
        oc.add( "person" );
        oc.add( "organizationalPerson" );
        oc.add( "inetOrgPerson" );
        user.put( oc );
        user.put( "sn", "Bush" );
        user.put( "userPassword", "Aerial" );
        ctx.createSubcontext( "cn=Kate Bush", user );

        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_CREDENTIALS, "Aerial" );
        env.put( Context.SECURITY_PRINCIPAL, "cn=Kate Bush,dc=aPache,dc=org" );

        InitialDirContext userCtx = new InitialDirContext( env );
        assertNotNull( userCtx );
    }


    @Test
    public void testFailureWithUnsupportedControl() throws Exception
    {
        Control unsupported = new ControlImpl( "1.1.1.1" )
        {
            boolean isCritical = true;
            private static final long serialVersionUID = 1L;


            @SuppressWarnings("unused")
            public String getType()
            {
                return "1.1.1.1";
            }


            public byte[] getValue()
            {
                return new byte[0];
            }


            public void setValue( byte[] value )
            {
            }


            public boolean isCritical()
            {
                return isCritical;
            }


            public void setCritical( boolean isCritical )
            {
                this.isCritical = isCritical;
            }


            public String getOid()
            {
                return "1.1.1.1";
            }
        };
        
        ldapServer.getDirectoryService().setAllowAnonymousAccess( true );
        
        Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() + "/ou=system" );
        env.put( "java.naming.ldap.version", "3" );
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=admin,ou=system" );
        InitialLdapContext ctx = new InitialLdapContext( env, null );

        Attributes user = new BasicAttributes( "cn", "Kate Bush", true );
        Attribute oc = new BasicAttribute( "objectClass" );
        oc.add( "top" );
        oc.add( "person" );
        oc.add( "organizationalPerson" );
        oc.add( "inetOrgPerson" );
        user.put( oc );
        user.put( "sn", "Bush" );
        user.put( "userPassword", "Aerial" );
        ctx.setRequestControls( JndiUtils.toJndiControls( new Control[]
                {unsupported} ) );

        try
        {
            ctx.createSubcontext( "cn=Kate Bush", user );
            fail();
        }
        catch ( OperationNotSupportedException e )
        {
        }

        unsupported.setCritical( false );
        ctx.setRequestControls( JndiUtils.toJndiControls( new Control[]{unsupported} ) );
        
        DirContext kate = ctx.createSubcontext( "cn=Kate Bush", user );
        assertNotNull( kate );
        assertTrue( ArrayUtils.isEquals( Asn1StringUtils.getBytesUtf8( "Aerial" ), kate.getAttributes( "" ).get(
                "userPassword" ).get() ) );
    }
}
