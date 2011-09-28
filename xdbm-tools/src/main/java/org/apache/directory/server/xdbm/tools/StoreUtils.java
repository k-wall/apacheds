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
package org.apache.directory.server.xdbm.tools;


import java.util.UUID;

import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.csn.CsnFactory;
import org.apache.directory.shared.ldap.cursor.Cursor;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.client.DefaultClientAttribute;
import org.apache.directory.shared.ldap.entry.client.DefaultClientEntry;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * A utility class for loading example LDIF data.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class StoreUtils
{
    /** CSN factory instance */
    private static final CsnFactory CSN_FACTORY = new CsnFactory( 0 );


    /**
     * Initializes and loads a store with the example data shown in
     * <a href="http://cwiki.apache.org/confluence/display/DIRxSRVx11/Structure+and+Organization">
     * Structure and Organization</a>
     *
     * TODO might want to make this load an LDIF instead in the future
     * TODO correct size of spaces in user provided DN
     * 
     * @param store the store object to be initialized
     * @param registries oid registries
     * @throws Exception on access exceptions
     */
    public static void loadExampleData( Store<ServerEntry, Long> store, SchemaManager schemaManager ) throws Exception
    {
        store.setSuffixDn( "o=Good Times Co." );

        DN suffixDn = new DN( "o=Good Times Co." );
        suffixDn.normalize( schemaManager.getNormalizerMapping() );

        store.init( schemaManager );

        // Entry #1
        DefaultServerEntry entry = new DefaultServerEntry( schemaManager, suffixDn );
        entry.add( "objectClass", "organization" );
        entry.add( "o", "Good Times Co." );
        entry.add( "postalCode", "1" );
        entry.add( "postOfficeBox", "1" );
        injectEntryInStore( store, entry );

        // Entry #2
        DN dn = new DN( "ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "organizationalUnit" );
        entry.add( "ou", "Sales" );
        entry.add( "postalCode", "1" );
        entry.add( "postOfficeBox", "1" );
        injectEntryInStore( store, entry );

        // Entry #3
        dn = new DN( "ou=Board of Directors,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "organizationalUnit" );
        entry.add( "ou", "Board of Directors" );
        entry.add( "postalCode", "1" );
        entry.add( "postOfficeBox", "1" );
        injectEntryInStore( store, entry );

        // Entry #4
        dn = new DN( "ou=Engineering,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "organizationalUnit" );
        entry.add( "ou", "Engineering" );
        entry.add( "postalCode", "2" );
        entry.add( "postOfficeBox", "2" );
        injectEntryInStore( store, entry );

        // Entry #5
        dn = new DN( "cn=JOhnny WAlkeR,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Sales" );
        entry.add( "cn", "JOhnny WAlkeR" );
        entry.add( "sn", "WAlkeR" );
        entry.add( "postalCode", "3" );
        entry.add( "postOfficeBox", "3" );
        injectEntryInStore( store, entry );

        // Entry #6
        dn = new DN( "cn=JIM BEAN,ou=Sales,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Sales" );
        entry.add( "cn", "JIM BEAN" );
        entry.add( "surName", "BEAN" );
        entry.add( "postalCode", "4" );
        entry.add( "postOfficeBox", "4" );
        injectEntryInStore( store, entry );

        // Entry #7
        dn = new DN( "ou=Apache,ou=Board of Directors,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "organizationalUnit" );
        entry.add( "ou", "Apache" );
        entry.add( "postalCode", "5" );
        entry.add( "postOfficeBox", "5" );
        injectEntryInStore( store, entry );

        // Entry #8
        dn = new DN( "cn=Jack Daniels,ou=Engineering,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "person", "organizationalPerson" );
        entry.add( "ou", "Engineering" );
        entry.add( "cn", "Jack Daniels" );
        entry.add( "SN", "Daniels" );
        entry.add( "postalCode", "6" );
        entry.add( "postOfficeBox", "6" );
        injectEntryInStore( store, entry );

        // aliases -------------

        // Entry #9
        dn = new DN( "commonName=Jim Bean,ou=Apache,ou=Board of Directors,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "alias", "extensibleObject" );
        entry.add( "ou", "Apache" );
        entry.add( "commonName", "Jim Bean" );
        entry.add( "aliasedObjectName", "cn=Jim Bean,ou=Sales,o=Good Times Co." );
        injectEntryInStore( store, entry );

        // Entry #10
        dn = new DN( "commonName=Jim Bean,ou=Board of Directors,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "alias", "extensibleObject" );
        entry.add( "commonName", "Jim Bean" );
        entry.add( "aliasedObjectName", "cn=Jim Bean,ou=Sales,o=Good Times Co." );
        injectEntryInStore( store, entry );

        // Entry #11
        dn = new DN( "2.5.4.3=Johnny Walker,ou=Engineering,o=Good Times Co." );
        dn.normalize( schemaManager.getNormalizerMapping() );
        entry = new DefaultServerEntry( schemaManager, dn );
        entry.add( "objectClass", "top", "alias", "extensibleObject" );
        entry.add( "ou", "Engineering" );
        entry.add( "2.5.4.3", "Johnny Walker" );
        entry.add( "aliasedObjectName", "cn=Johnny Walker,ou=Sales,o=Good Times Co." );
        injectEntryInStore( store, entry );
    }


    /**
     * This is primarily a convenience method used to extract all the attributes
     * associated with an entry.
     *
     * @param store the store to get the attributes from
     * @param id the id of the entry to get index information for
     * @return the index names and values as an Attributes object
     * @throws Exception if there are failures accessing the underlying store
     */
    @SuppressWarnings("unchecked")
    public Entry getAttributes( Store<Object, Long> store, Long id ) throws Exception
    {
        Entry entry = new DefaultClientEntry();

        // Get the distinguishedName to id mapping
        entry.put( "_nDn", store.getEntryDn( id ) );
        entry.put( "_upDn", store.getEntryUpdn( id ) );
        entry.put( "_parent", Long.toString( store.getParentId( id ) ) );

        // Get all standard index attribute to value mappings
        for ( Index index : store.getUserIndices() )
        {
            Cursor<ForwardIndexEntry> list = index.reverseCursor();
            ForwardIndexEntry recordForward = new ForwardIndexEntry();
            recordForward.setId( id );
            list.before( recordForward );

            while ( list.next() )
            {
                IndexEntry rec = list.get();
                String val = rec.getValue().toString();
                String attrId = index.getAttribute().getName();
                EntryAttribute attr = entry.get( attrId );

                if ( attr == null )
                {
                    attr = new DefaultClientAttribute( attrId );
                }

                attr.add( val );
                entry.put( attr );
            }
        }

        // Get all existence mappings for this id creating a special key
        // that looks like so 'existence[attribute]' and the value is set to id
        IndexCursor<String, Object, Long> list = store.getPresenceIndex().reverseCursor();
        ForwardIndexEntry recordForward = new ForwardIndexEntry();
        recordForward.setId( id );
        list.before( recordForward );
        StringBuffer val = new StringBuffer();

        while ( list.next() )
        {
            IndexEntry rec = list.get();
            val.append( "_existence[" );
            val.append( rec.getValue().toString() );
            val.append( "]" );

            String valStr = val.toString();
            EntryAttribute attr = entry.get( valStr );

            if ( attr == null )
            {
                attr = new DefaultClientAttribute( valStr );
            }

            attr.add( rec.getId().toString() );
            entry.put( attr );
            val.setLength( 0 );
        }

        // Get all parent child mappings for this entry as the parent using the
        // key 'child' with many entries following it.
        IndexCursor<Long, Object, Long> children = store.getOneLevelIndex().forwardCursor();
        ForwardIndexEntry longRecordForward = new ForwardIndexEntry();
        recordForward.setId( id );
        children.before( longRecordForward );

        EntryAttribute childAttr = new DefaultClientAttribute( "_child" );
        entry.put( childAttr );

        while ( children.next() )
        {
            IndexEntry rec = children.get();
            childAttr.add( rec.getId().toString() );
        }

        return entry;
    }


    /**
     * 
     * adds a given <i>ServerEntry</i> to the store after injecting entryCSN and entryUUID operational
     * attributes
     *
     * @param store the store
     * @param dn the normalized DN
     * @param entry the server entry
     * @throws Exception in case of any problems in adding the entry to the store
     */
    public static void injectEntryInStore( Store<ServerEntry, Long> store, ServerEntry entry ) throws Exception
    {
        entry.add( SchemaConstants.ENTRY_CSN_AT, CSN_FACTORY.newInstance().toString() );
        entry.add( SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString() );

        store.add( entry );
    }
}
