/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.changelog;



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.ldif.ChangeType;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifRevertor;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.normalizers.NoOpNormalizer;
import org.apache.directory.shared.ldap.schema.normalizers.OidNormalizer;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.StringTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests the MemoryChangeLogStore.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class MemoryChangeLogStoreTest
{
    MemoryChangeLogStore store;

    Map<String, OidNormalizer> oidsMap = new HashMap<String, OidNormalizer>();
    
    
    @Before
    public void setUp() throws Exception
    {
        store = new MemoryChangeLogStore();

        // The uid/UserId/0.9.2342.19200300.100.1.1 normalizer
        OidNormalizer uidOidNormalizer = new OidNormalizer( SchemaConstants.UID_AT_OID, 
            new NoOpNormalizer( SchemaConstants.UID_AT_OID ) );
        
        oidsMap.put( SchemaConstants.UID_AT, uidOidNormalizer );
        oidsMap.put( SchemaConstants.USER_ID_AT, uidOidNormalizer );
        oidsMap.put( SchemaConstants.UID_AT_OID, uidOidNormalizer );
        
        // The ou/organizationalUnit/2.5.4.11 normalizer
        OidNormalizer ouOidNormalizer = new OidNormalizer( SchemaConstants.OU_AT_OID, 
            new NoOpNormalizer( SchemaConstants.OU_AT_OID ) );
        
        oidsMap.put( SchemaConstants.OU_AT, ouOidNormalizer );
        oidsMap.put( SchemaConstants.ORGANIZATIONAL_UNIT_NAME_AT, ouOidNormalizer );
        oidsMap.put( SchemaConstants.OU_AT_OID, ouOidNormalizer );
    }


    @After
    public void tearDown() throws Exception
    {
        store = null;
    }


    @Test
    public void testLogCheckRevision() throws Exception
    {
        assertEquals( "first revision is always 0", 0, store.getCurrentRevision() );

        LdifEntry forward = new LdifEntry();
        forward.setDn( "ou=system" );
        forward.setChangeType( ChangeType.Add );
        forward.putAttribute( "objectClass", "organizationalUnit" );
        forward.putAttribute( "ou", "system" );

        LdifEntry reverse = LdifRevertor.reverseAdd( new DN( forward.getDn() ) );
        assertEquals( 1, store.log( new LdapPrincipal(), forward, reverse ).getRevision() );
        assertEquals( 1, store.getCurrentRevision() );
    }
    
    
    @Test
    public void testChangeLogSerialization() throws LdapException, IOException, ClassNotFoundException
    {
        DN systemDn = new DN( "ou=system" );
        systemDn.normalize( oidsMap );
        
        DN adminDn = new DN( "uid=admin, ou=system" );
        adminDn.normalize( oidsMap );

        LdifEntry forward = new LdifEntry();
        forward.setDn( systemDn );
        forward.setChangeType( ChangeType.Add );
        forward.putAttribute( "objectClass", "organizationalUnit" );
        forward.putAttribute( "ou", "system" );
        
        DN reverseDn = new DN( forward.getDn() );
        reverseDn.normalize( oidsMap );

        LdifEntry reverse = LdifRevertor.reverseAdd( reverseDn );

        String zuluTime = DateUtils.getGeneralizedTime();
        long revision = 1L;
        
        LdapPrincipal principal = new LdapPrincipal( adminDn, AuthenticationLevel.SIMPLE, StringTools.getBytesUtf8( "secret"  ) );
        ChangeLogEvent event = new ChangeLogEvent( revision, zuluTime, principal, forward, reverse );
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream( baos );

        out.writeObject( event );
        
        byte[] data = baos.toByteArray();
        ObjectInputStream in = new ObjectInputStream( new ByteArrayInputStream( data ) );
        
        ChangeLogEvent read = (ChangeLogEvent)in.readObject(); 
        
        // The read event should not be equal to the written event, as
        // the principal's password has not been stored
        assertNotSame( event, read );
        
        LdapPrincipal readPrincipal = read.getCommitterPrincipal();
        
        assertEquals( principal.getAuthenticationLevel(), readPrincipal.getAuthenticationLevel() );
        assertEquals( principal.getName(), readPrincipal.getName() );
        assertEquals( principal.getClonedName(), readPrincipal.getClonedName() );
        assertNull( readPrincipal.getUserPassword() );
        
        assertEquals( zuluTime, read.getZuluTime() );
        assertEquals( revision, read.getRevision() );
        assertEquals( forward, read.getForwardLdif() );
        assertEquals( reverse, read.getReverseLdifs().get( 0 ) );
    }
}
