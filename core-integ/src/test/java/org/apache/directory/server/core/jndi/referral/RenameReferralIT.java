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
package org.apache.directory.server.core.jndi.referral;

import static org.apache.directory.server.core.integ.IntegrationUtils.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.PartialResultException;
import javax.naming.ReferralException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.exception.LdapEntryAlreadyExistsException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.exception.LdapPartialResultException;
import org.apache.directory.shared.ldap.exception.LdapReferralException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests the referral handling functionality for the Modify operation 
 * within the server's core.
 * 
 * The Move operation is a ModifyDN where the RDN is changed, not the superior.
 * 
 * All the tests are described on this page :
 * http://cwiki.apache.org/confluence/display/DIRxSRVx11/Referral+Handling+Changes
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 691179 $
 */
@RunWith ( FrameworkRunner.class )
@ApplyLdifs( {
    // Root
    "dn: c=WW,ou=system",
    "objectClass: country",
    "objectClass: top",
    "c: WW",
    
    // Sub-root
    "dn: o=MNN,c=WW,ou=system",
    "objectClass: organization",
    "objectClass: top",
    "o: MNN",
    
    // Another Sub-root
    "dn: o=PNN,c=WW,ou=system",
    "objectClass: organization",
    "objectClass: top",
    "o: MNN",
    
    // Referral #1
    "dn: ou=Roles,o=MNN,c=WW,ou=system",
    "objectClass: extensibleObject",
    "objectClass: referral",
    "objectClass: top",
    "ou: Roles",
    "ref: ldap://hostd/ou=Roles,dc=apache,dc=org",
    
    // Referral #2
    "dn: ou=People,o=MNN,c=WW,ou=system",
    "objectClass: extensibleObject",
    "objectClass: referral",
    "objectClass: top",
    "ou: People",
    "ref: ldap://hostb/OU=People,DC=example,DC=com",
    "ref: ldap://hostc/OU=People,O=MNN,C=WW",
    
    // Entry # 1
    "dn: cn=Alex Karasulu,o=MNN,c=WW,ou=system",
    "objectClass: person",
    "objectClass: top",
    "cn: Alex Karasulu",
    "sn: akarasulu",
    
    // Entry # 2
    "dn: cn=Alex,o=MNN,c=WW,ou=system",
    "objectClass: person",
    "objectClass: top",
    "cn: Alex",
    "sn: akarasulu"
    }
)
public class RenameReferralIT extends AbstractLdapTestUnit
{

    /** The Context we are using to inject entries with JNDI */
    LdapContext MNNCtx;
    
    /** The entries we are using to do the tests */
    Attributes userEntry;
    ServerEntry serverEntry;
    
    @Before
    public void setUp() throws Exception
    {
        MNNCtx = getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, "o=MNN,c=WW,ou=system" );

        // JNDI entry
        userEntry = new BasicAttributes( "objectClass", "top", true );
        userEntry.get( "objectClass" ).add( "person" );
        userEntry.put( "sn", "elecharny" );
        userEntry.put( "cn", "Emmanuel Lecharny" );
        
        // Core API entry
        DN dn = new DN( "cn=Emmanuel Lecharny, ou=apache, ou=people, o=MNN, c=WW, ou=system" );
        serverEntry = new DefaultServerEntry( service.getSchemaManager(), dn );

        serverEntry.put( "ObjectClass", "top", "person" );
        serverEntry.put( "sn", "elecharny" );
        serverEntry.put( "cn", "Emmanuel Lecharny" );
    }

    
    /**
     * Test a rename of a non existing entry (not a referral), with no referral 
     * in its ancestor.
     */
    @Test
    public void testRenameNotExistingSuperiorNoReferralAncestor() throws Exception
    {
        try
        {
            MNNCtx.rename( "cn=Emmanuel Lecharny", "cn=Alex Karasulu" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }
    }


    /**
     * Test a rename of a non existing entry (not a referral), with a referral 
     * in its ancestor, using JNDI throw.
     */
    @Test
    public void testRenameNotExistingSuperiorReferralAncestorJNDIThrow() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "throw" );
            MNNCtx.rename( "cn=Emmanuel Lecharny,ou=apache,ou=roles", "cn=Alex Karasulu,ou=apache,ou=roles" );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=apache,ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }


    /**
     * Test a rename of a non existing entry (not a referral), with a referral 
     * in its ancestor, using JNDI ignore.
     */
    @Test
    public void testRenameNotExistingSuperiorReferralAncestorJNDIIgnore() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "ignore" );
            MNNCtx.rename( "cn=Emmanuel Lecharny,ou=apache,ou=roles", "cn=Alex Karasulu,ou=apache,ou=roles" );
            fail();
        }
        catch ( PartialResultException pre )
        {
            assertTrue( true );
        }
    }


    /**
     * Test a rename of a non existing entry (not a referral), with a referral 
     * in its ancestor, using the Core API without the ManageDsaIT flag.
     */
    @Test
    public void testRenameNotExistingSuperiorReferralAncestorCoreAPIWithoutManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();

        try
        {
            DN dn = new DN( "cn=Emmanuel Lecharny,ou=apache,ou=roles,o=MNN,c=WW,ou=system" );
            RDN newRdn = new RDN( "cn=Alex Karasulu" );
            session.rename( dn, newRdn, false, false );
            fail();
        }
        catch ( LdapReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=apache,ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }


    /**
     * Test a rename of a non existing entry (not a referral), with a referral 
     * in its ancestor, using the Core API with the ManageDsaIT flag.
     */
    @Test
    public void testRenameNotExistingSuperiorReferralAncestorCoreAPIWithManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        try
        {
            DN dn = new DN( "cn=Emmanuel Lecharny,ou=apache,ou=roles,o=MNN,c=WW,ou=system" );
            RDN newRdn = new RDN( "cn=Alex Karasulu" );
            session.rename( dn, newRdn, false, true );
            fail();
        }
        catch ( LdapPartialResultException lpre )
        {
            assertTrue( true );
        }
    }


    /**
     * Test a rename of an existing entry (not a referral), with no referral 
     * in its ancestor.
     */
    @Test
    public void testRenameExistingSuperiorNotExistingNewRdnNoReferralAncestor() throws Exception
    {
        // First check that the object exists
        Object renamed = MNNCtx.lookup( "cn=Alex Karasulu" );
        assertNotNull( renamed );

        // and that the target entry is not present
        try
        {
            renamed = MNNCtx.lookup( "cn=Emmanuel Lecharny" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }

        // Rename it
        MNNCtx.rename( "cn=Alex Karasulu", "cn=Emmanuel Lecharny" );
        
        // It should not be there anymore
        try
        {
            renamed = MNNCtx.lookup( "cn=Alex Karasulu" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }

        // But the new one should be there
        renamed = MNNCtx.lookup( "cn=Emmanuel Lecharny" );
        assertNotNull( renamed );
    }


    /**
     * Test a rename of an existing referral, using JNDI throw. 
     */
    @Test
    public void testRenameExistingReferralJNDIThrow() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "throw" );
            MNNCtx.rename( "ou=roles", "cn=Alex Karasulu" );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }


    /**
     * Test a rename of an existing referral, using JNDI ignore. 
     */
    @Test
    public void testRenameExistingReferralJNDIIgnore() throws Exception
    {
        MNNCtx.addToEnvironment( DirContext.REFERRAL, "ignore" );

        // First check that the object exists
        Object renamed = MNNCtx.lookup( "ou=Roles" );
        assertNotNull( renamed );

        // Also check that the new entry does not exist
        try
        {
            renamed = MNNCtx.lookup( "ou=Groups" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }
        
        // Now renames the referral
        MNNCtx.rename( "ou=roles", "ou=groups" );

        // It should not be there anymore
        try
        {
            renamed = MNNCtx.lookup( "ou=Roles" );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }

        // But the new one should be there
        renamed = MNNCtx.lookup( "ou=groups" );
        assertNotNull( renamed );
    }


    /**
     * Test a rename of an existing referral, using the Core API without 
     * the ManageDsaIt flag. 
     */
    @Test
    public void testRenameExistingReferralCoreApiWithoutManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();

        try
        {
            DN dn = new DN( "ou=roles,o=MNN,c=WW,ou=system" );
            RDN newRdn = new RDN( "cn=Alex Karasulu" );
            session.rename( dn, newRdn, false, false );
            fail();
        }
        catch ( LdapReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }


    /**
     * Test a rename of an existing referral,  using the Core API with
     * the ManageDsaIt flag.  
     */
    @Test
    public void testRenameExistingReferralCoreAPIWithManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        DN dnRoles = new DN( "ou=Roles,o=MNN,c=WW,ou=system" );
        DN dnGroups = new DN( "ou=Groups,o=MNN,c=WW,ou=system" );
        RDN newRdn = new RDN( "ou=Groups" );

        // First check that the object exists
        ServerEntry renamed = session.lookup( dnRoles );
        assertNotNull( renamed );

        // Also check that the new entry does not exist
        try
        {
            renamed = session.lookup( dnGroups );
            fail();
        }
        catch ( LdapNoSuchObjectException lnsoe )
        {
            assertTrue( true );
        }
        
        // Now renames the referral
        session.rename( dnRoles, newRdn, false, true );

        // It should not be there anymore
        try
        {
            renamed = session.lookup( dnRoles );
            fail();
        }
        catch ( LdapNoSuchObjectException lnsoe )
        {
            assertTrue( true );
        }

        // But the new one should be there
        renamed = session.lookup( dnGroups );
        assertNotNull( renamed );
    }


    /**
     * Test a rename an entry using an already existing RDN (the new entry already exists), not a referral
     */
    @Test
    public void testRenameRdnExistNotReferral() throws Exception
    {
        try
        {
            MNNCtx.rename( "cn=Alex Karasulu", "cn=Alex" );
            fail();
        }
        catch ( NameAlreadyBoundException nabe )
        {
            assertTrue( true );
        }
    }


    /**
     * Test a rename a referral using an already existing RDN (the new entry already exists and is a referral),
     * using JNDI throw
     */
    @Test
    public void testRenameRdnExistIsReferralJNDIThrow() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "throw" );
            MNNCtx.rename( "ou=Roles", "ou=People" );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }


    /**
     * Test a rename a referral using an already existing RDN (the new entry already exists and is a referral),
     * using JNDI ignore
     */
    @Test
    public void testRenameRdnExistIsReferralJNDIIgnore() throws Exception
    {
        try
        {
            MNNCtx.addToEnvironment( DirContext.REFERRAL, "ignore" );
            MNNCtx.rename( "ou=Roles", "ou=People" );
            fail();
        }
        catch ( NameAlreadyBoundException nabe )
        {
            assertTrue( true );
        }
    }


    /**
     * Test a rename a referral using an already existing RDN (the new entry already exists and is a referral),
     * using the Core API, without the ManageDsaIt flag
     */
    @Test
    public void testRenameRdnExistIsReferralCoreAPIWithoutManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        DN dn = new DN( "ou=Roles,o=MNN,c=WW,ou=system" );
        RDN newRdn = new RDN( "ou=People" );

        try
        {
            session.rename( dn, newRdn, false, false );
            fail();
        }
        catch ( LdapReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/ou=Roles,dc=apache,dc=org" );
            
            do 
            {
                String ref = (String)re.getReferralInfo();
                
                assertTrue( expectedRefs.contains( ref ) );
                nbRefs ++;
            }
            while ( re.skipReferral() );
            
            assertEquals( 1, nbRefs );
        }
    }


    /**
     * Test a rename a referral using an already existing RDN (the new entry already exists and is a referral),
     * using the Core API, with the ManageDsaIt flag
     */
    @Test
    public void testRenameRdnExistIsReferralCoreAPIWithManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        DN dn = new DN( "ou=Roles,o=MNN,c=WW,ou=system" );
        RDN newRdn = new RDN( "ou=People" );

        try
        {
            session.rename( dn, newRdn, false, true );
            fail();
        }
        catch ( LdapEntryAlreadyExistsException leaee )
        {
            assertTrue( true );
        }
    }
}
