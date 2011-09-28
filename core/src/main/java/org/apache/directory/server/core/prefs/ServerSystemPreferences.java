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


import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.ServerModification;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidDnException;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.util.PreferencesDictionary;


/**
 * A server side system {@link Preferences} implementation.  This implementation
 * presumes the creation of a root system preferences node in advance.  This
 * should be included with the system.ldif that is packaged with the server.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927839 $
 */
public class ServerSystemPreferences extends AbstractPreferences
{
    /** an empty array of Strings used to get array from list */
    private static final String[] EMPTY_STRINGS = new String[0];

    /** the changes representing cached alterations to preferences */
    private List<Modification> changes = new ArrayList<Modification>( 3 );

    /** maps changes based on key: key->list of mods (on same key) */
    private HashMap<String, List<Modification>> keyToChange = new HashMap<String, List<Modification>>( 3 );
    
    private DN dn;
    
    private DirectoryService directoryService;
    


    /**
     * Creates a preferences object for the system preferences root.
     * @param directoryService the directory service core
     */
    public ServerSystemPreferences( DirectoryService directoryService )
    {
        super( null, "" );
        super.newNode = false;
        
        try
        {
            dn = new DN( "prefNodeName=sysPrefRoot,ou=system" );
        }
        catch ( LdapInvalidDnException e )
        {
            // never happens
        }
        
        this.directoryService = directoryService;
    }

    
    public void close() throws LdapException
    {
    }


    /**
     * Creates a preferences object using a relative name.
     * 
     * @param name the name of the preference node to create
     * @param parent the parent of the preferences node to create
     */
    public ServerSystemPreferences( ServerSystemPreferences parent, String name )
    {
        super( parent, name );

        this.directoryService = parent.directoryService;
        DN parentDn = ( ( ServerSystemPreferences ) parent() ).dn;
        try
        {
            dn = new DN( "prefNodeName=" + name + "," + parentDn.getName() );
            dn.normalize( directoryService.getSchemaManager().getNormalizerMapping() );
            
            if ( ! directoryService.getAdminSession().exists( dn ) )
            {
                ServerEntry entry = directoryService.newEntry( dn );
                entry.add( SchemaConstants.OBJECT_CLASS_AT, SchemaConstants.TOP_OC, 
                    ApacheSchemaConstants.PREF_NODE_OC, SchemaConstants.EXTENSIBLE_OBJECT_OC );
                entry.add( "prefNodeName", name );
    
                directoryService.getAdminSession().add( entry );
                
                super.newNode = false;
            }
        }
        catch ( Exception e )
        {
            throw new ServerSystemPreferenceException( I18n.err( I18n.ERR_270 ), e );
        }
    }


    // ------------------------------------------------------------------------
    // Utility Methods
    // ------------------------------------------------------------------------

    /**
     * Wraps this ServerPreferences object as a Dictionary.
     *
     * @return a Dictionary that uses this ServerPreferences object as the underlying backing store
     */
    public Dictionary<String, String> wrapAsDictionary()
    {
        return new PreferencesDictionary( this );
    }


    // ------------------------------------------------------------------------
    // Protected SPI Methods
    // ------------------------------------------------------------------------

    protected void flushSpi() throws BackingStoreException
    {
        if ( changes.isEmpty() )
        {
            return;
        }

        try
        {
            directoryService.getAdminSession().modify( dn, changes );
        }
        catch ( Exception e )
        {
            throw new BackingStoreException( e );
        }

        changes.clear();
        keyToChange.clear();
    }


    protected void removeNodeSpi() throws BackingStoreException
    {
        try
        {
            directoryService.getAdminSession().delete( dn );
        }
        catch ( Exception e )
        {
            throw new BackingStoreException( e );
        }

        changes.clear();
        keyToChange.clear();
    }


    protected void syncSpi() throws BackingStoreException
    {
        if ( changes.isEmpty() )
        {
            return;
        }

        try
        {
            directoryService.getAdminSession().modify( dn, changes );
        }
        catch ( Exception e )
        {
            throw new BackingStoreException( e );
        }

        changes.clear();
        keyToChange.clear();
    }


    protected String[] childrenNamesSpi() throws BackingStoreException
    {
        List<String> children = new ArrayList<String>();
        EntryFilteringCursor list;

        try
        {
            list = directoryService.getAdminSession().list( dn, AliasDerefMode.DEREF_ALWAYS, null );
            list.beforeFirst();
            while ( list.next() )
            {
                ClonedServerEntry entry = list.get();
                children.add( ( String ) entry.getDn().getRdn().getNormValue() );
            }
        }
        catch ( Exception e )
        {
            throw new BackingStoreException( e );
        }

        return children.toArray( EMPTY_STRINGS );
    }


    protected String[] keysSpi() throws BackingStoreException
    {
        List<String> keys = new ArrayList<String>();

        try
        {
            ServerEntry entry = directoryService.getAdminSession().lookup( dn );

            for ( EntryAttribute attr : entry )
            {
                String oid = attr.getAttributeType().getOid();
                
                if ( oid.equals( SchemaConstants.OBJECT_CLASS_AT_OID ) )
                {
                    continue;
                }
                
                keys.add( attr.getUpId() );
            }
        }
        catch ( Exception e )
        {
            throw new BackingStoreException( e );
        }

        return keys.toArray( EMPTY_STRINGS );
    }


    protected void removeSpi( String key ) 
    {
        AttributeType at;
        try
        {
            at = directoryService.getSchemaManager().lookupAttributeTypeRegistry( key );
            EntryAttribute attr = new DefaultServerAttribute( at );
            Modification mi = new ServerModification( ModificationOperation.REMOVE_ATTRIBUTE, attr );
            addDelta( mi );
        }
        catch ( LdapException e )
        {
            e.printStackTrace();
        }
    }


    private void addDelta( Modification mi )
    {
        String key = mi.getAttribute().getUpId();
        List<Modification> deltas;
        changes.add( mi );
        
        if ( keyToChange.containsKey( key ) )
        {
            deltas = keyToChange.get( key );
        }
        else
        {
            deltas = new ArrayList<Modification>();
        }

        deltas.add( mi );
        keyToChange.put( key, deltas );
    }


    protected String getSpi( String key )
    {
        try
        {
            EntryAttribute attr = directoryService.getAdminSession().lookup( dn ).get( key );
            
            if ( keyToChange.containsKey( key ) )
            {
                for ( Modification mod : keyToChange.get( key ) )
                {
                    if ( mod.getOperation() == ModificationOperation.REMOVE_ATTRIBUTE )
                    {
                        attr = null;
                    }
                    else
                    {
                        attr = mod.getAttribute();
                    }
                }
            }

            if ( attr == null )
            {
                return null;
            }
            else
            {
                return attr.getString();
            }
        }
        catch ( Exception e )
        {
            throw new ServerSystemPreferenceException( I18n.err( I18n.ERR_271 ), e );
        }
    }


    protected void putSpi( String key, String value )
    {
        AttributeType at;
        try
        {
            at = directoryService.getSchemaManager().lookupAttributeTypeRegistry( key );
            EntryAttribute attr = new DefaultServerAttribute( at, value );
            Modification mi = new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, attr );
            addDelta( mi );
        }
        catch ( LdapException e )
        {
            e.printStackTrace();
        }
    }


    protected AbstractPreferences childSpi( String name )
    {
        return new ServerSystemPreferences( this, name );
    }
}
