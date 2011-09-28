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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.PartialResultException;
import javax.naming.ReferralException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapContext;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.client.ClientModification;
import org.apache.directory.shared.ldap.entry.client.DefaultClientAttribute;
import org.apache.directory.shared.ldap.exception.LdapNoSuchObjectException;
import org.apache.directory.shared.ldap.exception.LdapReferralException;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests the referral handling functionality for the Modify operation 
 * within the server's core.
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
    "sn: akarasulu"
    }
)
public class ModifyReferralIT extends AbstractLdapTestUnit
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
     * Test modification of a non existing entry (not a referral), with no referral 
     * in its ancestor, using JNDI.
     */
    @Test
    public void testModifyNonExistingEntry() throws Exception
    {
        try
        {
            Attribute description = new BasicAttribute( "description", "This is a description" );
            Attributes attrs = new BasicAttributes( true );
            attrs.put( description );
            
            MNNCtx.modifyAttributes( "cn=Emmanuel Lecharny", DirContext.ADD_ATTRIBUTE, attrs );
            fail();
        }
        catch ( NameNotFoundException nnfe )
        {
            assertTrue( true );
        }
    }


    /**
     * Test a modification of an entry with an ancestor referral, using JNDI,
     * with 'throw'
     */
    @Test
    public void testModifyEntryWithAncestorJNDIThrow() throws Exception
    {
        try
        {
            // Set to 'throw'
            MNNCtx.addToEnvironment( Context.REFERRAL, "throw" );

            Attribute description = new BasicAttribute( "description", "This is a description" );
            Attributes attrs = new BasicAttributes( true );
            attrs.put( description );

            MNNCtx.modifyAttributes( "cn=Emmanuel Lecharny,ou=Roles", DirContext.ADD_ATTRIBUTE, attrs );
            fail();
        }
        catch ( ReferralException re )
        {
            int nbRefs = 0;
            Set<String> expectedRefs = new HashSet<String>();
            expectedRefs.add( "ldap://hostd/cn=Emmanuel%20Lecharny,ou=Roles,dc=apache,dc=org" );
            
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
     * Test a modification of an entry with an ancestor referral, using JNDI,
     * with 'ignore'
     */
    @Test
    public void testModifyEntryWithAncestorJNDIIgnore() throws Exception
    {
        try
        {
            // Set to 'throw'
            MNNCtx.addToEnvironment( Context.REFERRAL, "ignore" );

            Attribute description = new BasicAttribute( "description", "This is a description" );
            Attributes attrs = new BasicAttributes( true );
            attrs.put( description );

            MNNCtx.modifyAttributes( "cn=Emmanuel Lecharny,ou=Roles", DirContext.ADD_ATTRIBUTE, attrs );
            fail();
        }
        catch ( PartialResultException pre )
        {
            assertTrue( true );
        }
    }


    /**
     * Test the modification of an entry with an ancestor referral, using the core API,
     * without a ManageDsaIT.
     */
    @Test
    public void testModifyEntryWithAncestorCoreAPIWithoutManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        
        try
        {
            EntryAttribute attr = new DefaultClientAttribute( "Description", "this is a test" );
            Modification mod = new ClientModification(
                ModificationOperation.ADD_ATTRIBUTE, attr );
            List<Modification> mods = new ArrayList<Modification>();
            
            mods.add( mod );
            
            session.modify( new DN( "cn=Emmanuel Lecharny,ou=Roles,c=MNN,o=WW,ou=system" ), mods );
            fail();
        }
        catch ( LdapNoSuchObjectException lnsoe )
        {
            assertTrue( true );
        }
    }


    /**
     * Test the modification of an entry with an ancestor referral, using the core API,
     * with a ManageDsaIT flag.
     */
    @Test
    public void testModifyEntryWithAncestorCoreAPIWithManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        
        try
        {
            EntryAttribute attr = new DefaultClientAttribute( "Description", "this is a test" );
            Modification mod = new ClientModification(
                ModificationOperation.ADD_ATTRIBUTE, attr );
            List<Modification> mods = new ArrayList<Modification>();
            
            mods.add( mod );
            
            session.modify( new DN( "cn=Emmanuel Lecharny,ou=Roles,c=MNN,o=WW,ou=system" ), mods, true );
            fail();
        }
        catch ( LdapNoSuchObjectException lnsoe )
        {
            assertTrue( true );
        }
    }
    
    
    /**
     * Test modification of an existing entry (not a referral), with no referral 
     * in its ancestor, using JNDI.
     */
    @Test
    public void testModifyExistingEntryNoReferral() throws Exception
    {
        Attribute description = new BasicAttribute( "description", "This is a description" );
        Attributes attrs = new BasicAttributes( true );
        attrs.put( description );
        
        MNNCtx.modifyAttributes( "cn=Alex Karasulu", DirContext.ADD_ATTRIBUTE, attrs );
        
        // Now try to retrieve this attribute
        Attributes result = MNNCtx.getAttributes( "cn=Alex Karasulu", new String[]{ "description" } );
        
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertEquals( "This is a description", result.get( "description" ).get() );
    }

    
    /**
     * Test modification of an existing referral entry, using JNDI "throw".
     */
    @Test
    public void testModifyExistingEntryReferralJNDIThrow() throws Exception
    {
        Attribute description = new BasicAttribute( "description", "This is a description" );
        Attributes attrs = new BasicAttributes( true );
        attrs.put( description );
        
        try
        {
            MNNCtx.modifyAttributes( "ou=Roles", DirContext.ADD_ATTRIBUTE, attrs );
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
     * Test modification of an existing referral entry, using JNDI "ignore".
     */
    @Test
    public void testModifyExistingEntryReferralJNDIIgnore() throws Exception
    {
        Attribute description = new BasicAttribute( "description", "This is a description" );
        Attributes attrs = new BasicAttributes( true );
        attrs.put( description );
        
        MNNCtx.addToEnvironment( Context.REFERRAL, "ignore" );
        
        MNNCtx.modifyAttributes( "ou=Roles", DirContext.ADD_ATTRIBUTE, attrs );
        
        // Now try to retrieve this attribute
        Attributes result = MNNCtx.getAttributes( "ou=Roles", new String[]{ "description" } );
        
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertEquals( "This is a description", result.get( "description" ).get() );
    }

    
    /**
     * Test modification of an existing referral entry, using the Core API 
     * and no ManageDsaIT flag
     */
    @Test
    public void testModifyExistingEntryReferralCoreAPIWithoutManageDsaIt() throws Exception
    {
        CoreSession session = service.getAdminSession();
        
        try
        {
            EntryAttribute attr = new DefaultClientAttribute( "Description", "this is a test" );
            Modification mod = new ClientModification(
                ModificationOperation.ADD_ATTRIBUTE, attr );
            List<Modification> mods = new ArrayList<Modification>();
            
            mods.add( mod );
            
            session.modify( new DN( "ou=Roles,o=MNN,c=WW,ou=system" ), mods, false );
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
     * Test modification of an existing referral entry, using the Core API 
     * and the ManageDsaIT flag
     */
    @Test
    public void testModifyExistingEntryReferralCoreAPIManageDsaIT() throws Exception
    {
        CoreSession session = service.getAdminSession();
        
        EntryAttribute attr = new DefaultClientAttribute( "Description", "This is a description" );
        Modification mod = new ClientModification(
            ModificationOperation.ADD_ATTRIBUTE, attr );
        List<Modification> mods = new ArrayList<Modification>();
        
        mods.add( mod );
        
        session.modify( new DN( "ou=Roles,o=MNN,c=WW,ou=system" ), mods, true );
        
        // Now try to retrieve this attribute
        Attributes result = MNNCtx.getAttributes( "ou=Roles", new String[]{ "description" } );
        
        assertNotNull( result );
        assertEquals( 1, result.size() );
        assertEquals( "This is a description", result.get( "description" ).get() );
    }
}
