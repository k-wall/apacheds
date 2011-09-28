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
package org.apache.directory.server.core.integ;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.jndi.ServerLdapContext;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.client.DefaultClientAttribute;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.ldif.ChangeType;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifReader;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.registries.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Integration test utility methods.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class IntegrationUtils
{
    /** The class logger */
    private static final Logger LOG = LoggerFactory.getLogger( IntegrationUtils.class );

    private static final List<LdapConnection> openConnections = new ArrayList<LdapConnection>();

    /**
     * Deletes the working directory.
     *
     * @param wkdir the working directory to delete
     * @throws IOException if the working directory cannot be deleted
     */
    public static void doDelete( File wkdir ) throws IOException
    {
        if ( wkdir.exists() )
        {
            try
            {
                FileUtils.deleteDirectory( wkdir );
            }
            catch ( IOException e )
            {
                LOG.error( I18n.err( I18n.ERR_115 ), e );
            }
        }
        if ( wkdir.exists() )
        {
            throw new IOException( I18n.err( I18n.ERR_116, wkdir ) );
        }
    }


    /**
     * Inject an ldif String into the server. DN must be relative to the
     * root.
     *
     * @param service the directory service to use 
     * @param ldif the ldif containing entries to add to the server.
     * @throws NamingException if there is a problem adding the entries from the LDIF
     */
    public static void injectEntries( DirectoryService service, String ldif ) throws Exception
    {
        LdifReader reader = new LdifReader();
        List<LdifEntry> entries = reader.parseLdif( ldif );

        for ( LdifEntry entry : entries )
        {
            if ( entry.isChangeAdd() )
            {
                service.getAdminSession().add( 
                    new DefaultServerEntry( service.getSchemaManager(), entry.getEntry() ) );
            }
            else if ( entry.isChangeModify() )
            {
                service.getAdminSession().modify( 
                    entry.getDn(), entry.getModificationItems() );
            }
            else
            {
                String message = I18n.err( I18n.ERR_117, entry.getChangeType() );
                LOG.error( message );
                throw new NamingException( message );
            }
        }
        
        // And close the reader
        reader.close();
    }


    public static LdifEntry getUserAddLdif() throws LdapException
    {
        return getUserAddLdif( "uid=akarasulu,ou=users,ou=system", "test".getBytes(), "Alex Karasulu", "Karasulu" );
    }


    public static LdapContext getContext( String principalDn, DirectoryService service, String dn )
            throws Exception
    {
        if ( principalDn == null )
        {
            principalDn = "";
        }

        DN userDn = new DN( principalDn );
        userDn.normalize( service.getSchemaManager().getNormalizerMapping() );
        LdapPrincipal principal = new LdapPrincipal( userDn, AuthenticationLevel.SIMPLE );

        if ( dn == null )
        {
            dn = "";
        }

        CoreSession session = service.getSession( principal );
        LdapContext ctx = new ServerLdapContext( service, session, new LdapName( dn ) );
        return ctx;
    }


    public static CoreSession getCoreSession( String principalDn, DirectoryService service, String dn )
        throws Exception
    {
        if ( principalDn == null )
        {
            principalDn = "";
        }
        
        DN userDn = new DN( principalDn );
        userDn.normalize( service.getSchemaManager().getNormalizerMapping() );
        LdapPrincipal principal = new LdapPrincipal( userDn, AuthenticationLevel.SIMPLE );
        
        if ( dn == null )
        {
            dn = "";
        }
        
        CoreSession session = service.getSession( principal );
        return session;
    }


    public static LdapContext getSystemContext( DirectoryService service ) throws Exception
    {
        return getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, ServerDNConstants.SYSTEM_DN );
    }


    public static LdapContext getSchemaContext( DirectoryService service ) throws Exception
    {
        return getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, SchemaConstants.OU_SCHEMA );
    }


    public static LdapContext getRootContext( DirectoryService service ) throws Exception
    {
        return getContext( ServerDNConstants.ADMIN_SYSTEM_DN, service, "" );
    }


    public static void apply( DirectoryService service, LdifEntry entry ) throws Exception
    {
        DN dn = new DN( entry.getDn() );
        CoreSession session = service.getAdminSession();

        switch( entry.getChangeType().getChangeType() )
        {
            case( ChangeType.ADD_ORDINAL ):
                session.add( 
                    new DefaultServerEntry( service.getSchemaManager(), entry.getEntry() ) ); 
                break;
                
            case( ChangeType.DELETE_ORDINAL ):
                session.delete( dn );
                break;
                
            case( ChangeType.MODDN_ORDINAL ):
            case( ChangeType.MODRDN_ORDINAL ):
                RDN newRdn = new RDN( entry.getNewRdn() );
            
                if ( entry.getNewSuperior() != null )
                {
                    // It's a move. The superior have changed
                    // Let's see if it's a rename too
                    RDN oldRdn = dn.getRdn();
                    DN newSuperior = new DN( entry.getNewSuperior() );
                    
                    if ( dn.size() == 0 )
                    {
                        throw new IllegalStateException( I18n.err( I18n.ERR_475 ) );
                    }
                    else if ( oldRdn.equals( newRdn ) )
                    {
                        // Same rdn : it's a move
                        session.move( dn, newSuperior );
                    }
                    else
                    {
                        // it's a move and rename 
                        session.moveAndRename( dn, newSuperior, newRdn, entry.isDeleteOldRdn() );
                    }
                }
                else
                {
                    // it's a rename
                    session.rename( dn, newRdn, entry.isDeleteOldRdn() );
                }
                
                break;

            case( ChangeType.MODIFY_ORDINAL ):
                session.modify( dn, entry.getModificationItems() );
                break;

            default:
                throw new IllegalStateException( I18n.err( I18n.ERR_476, entry.getChangeType() ) );
        }
    }


    public static LdifEntry getUserAddLdif( String dnstr, byte[] password, String cn, String sn )
            throws LdapException
    {
        DN dn = new DN( dnstr );
        LdifEntry ldif = new LdifEntry();
        ldif.setDn( dnstr );
        ldif.setChangeType( ChangeType.Add );

        EntryAttribute attr = new DefaultClientAttribute( "objectClass", 
            "top", "person", "organizationalPerson", "inetOrgPerson" );
        ldif.addAttribute( attr );

        attr = new DefaultClientAttribute( "ou", "Engineering", "People" );
        ldif.addAttribute( attr );

        String uid = ( String ) dn.getRdn().getNormValue();
        ldif.putAttribute( "uid", uid );

        ldif.putAttribute( "l", "Bogusville" );
        ldif.putAttribute( "cn", cn );
        ldif.putAttribute( "sn", sn );
        ldif.putAttribute( "mail", uid + "@apache.org" );
        ldif.putAttribute( "telephoneNumber", "+1 408 555 4798" );
        ldif.putAttribute( "facsimileTelephoneNumber", "+1 408 555 9751" );
        ldif.putAttribute( "roomnumber", "4612" );
        ldif.putAttribute( "userPassword", password );

        String givenName = cn.split( " " )[0];
        ldif.putAttribute( "givenName", givenName );
        return ldif;
    }

    // -----------------------------------------------------------------------
    // Enable/Disable Schema Tests
    // -----------------------------------------------------------------------


    public static void enableSchema( DirectoryService service, String schemaName ) throws Exception
    {
        LdapContext schemaRoot = getSchemaContext( service );

        // now enable the test schema
        ModificationItem[] mods = new ModificationItem[1];
        Attribute attr = new BasicAttribute( "m-disabled", "FALSE" );
        mods[0] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, attr );
        schemaRoot.modifyAttributes( "cn=" + schemaName, mods );
    }
    
    
    public static void disableSchema( DirectoryService service, String schemaName ) throws Exception
    {
        LdapContext schemaRoot = getSchemaContext( service );

        // now enable the test schema
        ModificationItem[] mods = new ModificationItem[1];
        Attribute attr = new BasicAttribute( "m-disabled", "TRUE" );
        mods[0] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, attr );
        schemaRoot.modifyAttributes( "cn=" + schemaName, mods );
    }
    
    
    /**
     * A helper method which tells if a schema is disabled.
     */
    public static boolean isDisabled( DirectoryService service, String schemaName )
    {
        Schema schema = service.getSchemaManager().getLoadedSchema( schemaName );
        
        return ( schema == null ) || schema.isDisabled();
    }
    
    
    /**
     * A helper method which tells if a schema is loaded.
     */
    public static boolean isLoaded( DirectoryService service, String schemaName )
    {
        Schema schema = service.getSchemaManager().getLoadedSchema( schemaName );
        
        return ( schema != null );
    }
    
    
    /**
     * A helper method which tells if a schema is enabled. A shema must be
     * loaded and enabled.
     */
    public static boolean isEnabled( DirectoryService service, String schemaName )
    {
        Schema schema = service.getSchemaManager().getLoadedSchema( schemaName );
        
        return ( schema != null ) && schema.isEnabled();
    }
    
    
    /**
     * gets a LdapConnection bound using the default admin DN uid=admin,ou=system and password "secret"
     */
    public static LdapConnection getAdminConnection( LdapServer ldapServer ) throws Exception
    {
        return getConnectionAs( ldapServer, ServerDNConstants.ADMIN_SYSTEM_DN, "secret" );
    }


    public static LdapConnection getConnectionAs( LdapServer ldapServer, String dn, String password ) throws Exception
    {
        return getConnectionAs( "localhost", ldapServer.getPort(), dn, password );
    }


    public static LdapConnection getConnectionAs( LdapServer ldapServer, DN dn, String password ) throws Exception
    {
        return getConnectionAs( "localhost", ldapServer.getPort(), dn.getName(), password );
    }


    public static LdapConnection getConnectionAs( String host, int port, String dn, String password ) throws Exception
    {
        LdapConnection connection = new LdapConnection( host, port );
        connection.bind( dn, password );
        openConnections.add( connection );
        return connection;
    }
    
    
    public static void closeConections()
    {
        
        for( LdapConnection con : openConnections )
        {
            if( con == null )
            {
                continue;
            }
            
            try
            {
                if( con.isConnected() )
                {
                    con.close();
                }
            }
            catch( Exception e )
            {
                // shouldn't happen, but print the stacktrace so that less pain during development to find the cause
                e.printStackTrace();
            }
        }
        
        openConnections.clear();
    }
}
