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
package org.apache.directory.server.core.subtree;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilter;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.partition.ByPassConstants;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.codec.search.controls.subentries.SubentriesControl;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.DefaultServerAttribute;
import org.apache.directory.shared.ldap.entry.DefaultServerEntry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.entry.ServerModification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.NormalizerMappingResolver;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.normalizers.OidNormalizer;
import org.apache.directory.shared.ldap.schema.registries.OidRegistry;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecification;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecificationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The Subentry interceptor service which is responsible for filtering
 * out subentries on search operations and injecting operational attributes
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 928945 $
 */
public class SubentryInterceptor extends BaseInterceptor
{
    /** the subentry control OID */
    private static final String SUBENTRY_CONTROL = SubentriesControl.CONTROL_OID;

    public static final String AC_AREA = "accessControlSpecificArea";
    public static final String AC_INNERAREA = "accessControlInnerArea";

    public static final String SCHEMA_AREA = "subschemaAdminSpecificArea";

    public static final String COLLECTIVE_AREA = "collectiveAttributeSpecificArea";
    public static final String COLLECTIVE_INNERAREA = "collectiveAttributeInnerArea";

    public static final String TRIGGER_AREA = "triggerExecutionSpecificArea";
    public static final String TRIGGER_INNERAREA = "triggerExecutionInnerArea";

    public static final String[] SUBENTRY_OPATTRS =
        { SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT, SchemaConstants.SUBSCHEMA_SUBENTRY_AT,
            SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT, SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT };

    private static final Logger LOG = LoggerFactory.getLogger( SubentryInterceptor.class );

    /** the hash mapping the DN of a subentry to its SubtreeSpecification/types */
    private final SubentryCache subentryCache = new SubentryCache();

    private SubtreeSpecificationParser ssParser;
    private SubtreeEvaluator evaluator;
    private PartitionNexus nexus;

    /** The global registries */
    private SchemaManager schemaManager;

    /** The OID registry */
    private OidRegistry oidRegistry;

    private AttributeType objectClassType;


    public void init( DirectoryService directoryService ) throws Exception
    {
        super.init( directoryService );
        nexus = directoryService.getPartitionNexus();
        schemaManager = directoryService.getSchemaManager();
        oidRegistry = schemaManager.getGlobalOidRegistry();

        // setup various attribute type values
        objectClassType = schemaManager.lookupAttributeTypeRegistry( schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.OBJECT_CLASS_AT ) );

        ssParser = new SubtreeSpecificationParser( new NormalizerMappingResolver()
        {
            public Map<String, OidNormalizer> getNormalizerMapping() throws Exception
            {
                return schemaManager.getNormalizerMapping();
            }
        }, schemaManager.getNormalizerMapping() );
        evaluator = new SubtreeEvaluator( oidRegistry, schemaManager );

        // prepare to find all subentries in all namingContexts
        Set<String> suffixes = this.nexus.listSuffixes( null );
        ExprNode filter = new EqualityNode<String>( SchemaConstants.OBJECT_CLASS_AT, new StringValue(
            SchemaConstants.SUBENTRY_OC ) );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { SchemaConstants.SUBTREE_SPECIFICATION_AT, SchemaConstants.OBJECT_CLASS_AT } );

        // search each namingContext for subentries
        for ( String suffix:suffixes )
        {
            DN suffixDn = new DN( suffix );
            suffixDn.normalize( schemaManager.getNormalizerMapping() );

            DN adminDn = new DN( ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );
            adminDn.normalize( schemaManager.getNormalizerMapping() );
            CoreSession adminSession = new DefaultCoreSession( 
                new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );

            SearchOperationContext searchOperationContext = new SearchOperationContext( adminSession, suffixDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry subentry = subentries.get();
                DN dnName = subentry.getDn();

                String subtree = subentry.get( SchemaConstants.SUBTREE_SPECIFICATION_AT ).getString();
                SubtreeSpecification ss;

                try
                {
                    ss = ssParser.parse( subtree );
                }
                catch ( Exception e )
                {
                    LOG.warn( "Failed while parsing subtreeSpecification for " + dnName );
                    continue;
                }

                dnName.normalize( schemaManager.getNormalizerMapping() );
                subentryCache.setSubentry( dnName.getNormName(), ss, getSubentryTypes( subentry ) );
            }
        }
    }


    private int getSubentryTypes( ServerEntry subentry ) throws Exception
    {
        int types = 0;

        EntryAttribute oc = subentry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( oc == null )
        {
            throw new LdapSchemaViolationException( ResultCodeEnum.OBJECT_CLASS_VIOLATION,
                I18n.err( I18n.ERR_305 ) );
        }

        if ( oc.contains( SchemaConstants.ACCESS_CONTROL_SUBENTRY_OC ) )
        {
            types |= Subentry.ACCESS_CONTROL_SUBENTRY;
        }

        if ( oc.contains( "subschema" ) )
        {
            types |= Subentry.SCHEMA_SUBENTRY;
        }

        if ( oc.contains( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRY_OC ) )
        {
            types |= Subentry.COLLECTIVE_SUBENTRY;
        }

        if ( oc.contains( ApacheSchemaConstants.TRIGGER_EXECUTION_SUBENTRY_OC ) )
        {
            types |= Subentry.TRIGGER_SUBENTRY;
        }

        return types;
    }


    // -----------------------------------------------------------------------
    // Methods/Code dealing with Subentry Visibility
    // -----------------------------------------------------------------------

    
    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext opContext )
        throws Exception
    {
        EntryFilteringCursor cursor = nextInterceptor.list( opContext );

        if ( !isSubentryVisible( opContext ) )
        {
            cursor.addEntryFilter( new HideSubentriesFilter() );
        }

        return cursor;
    }


    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext opContext ) 
        throws Exception
    {
        EntryFilteringCursor cursor = nextInterceptor.search( opContext );

        // object scope searches by default return subentries
        if ( opContext.getScope() == SearchScope.OBJECT )
        {
            return cursor;
        }

        // for subtree and one level scope we filter
        if ( !isSubentryVisible( opContext ) )
        {
            cursor.addEntryFilter( new HideSubentriesFilter() );
        }
        else
        {
            cursor.addEntryFilter( new HideEntriesFilter() );
        }

        return cursor;
    }


    /**
     * Checks to see if subentries for the search and list operations should be
     * made visible based on the availability of the search request control
     *
     * @param invocation the invocation object to use for determining subentry visibility
     * @return true if subentries should be visible, false otherwise
     * @throws Exception if there are problems accessing request controls
     */
    private boolean isSubentryVisible( OperationContext opContext ) throws Exception
    {
        if ( !opContext.hasRequestControls() )
        {
            return false;
        }

        // found the subentry request control so we return its value
        if ( opContext.hasRequestControl( SUBENTRY_CONTROL ) )
        {
            SubentriesControl subentriesControl = ( SubentriesControl ) opContext.getRequestControl( SUBENTRY_CONTROL );
            return subentriesControl.isVisible();
        }

        return false;
    }


    // -----------------------------------------------------------------------
    // Methods dealing with entry and subentry addition
    // -----------------------------------------------------------------------

    /**
     * Evaluates the set of subentry subtrees upon an entry and returns the
     * operational subentry attributes that will be added to the entry if
     * added at the dn specified.
     *
     * @param dn the normalized distinguished name of the entry
     * @param entryAttrs the entry attributes are generated for
     * @return the set of subentry op attrs for an entry
     * @throws Exception if there are problems accessing entry information
     */
    public ServerEntry getSubentryAttributes( DN dn, ServerEntry entryAttrs ) throws Exception
    {
        ServerEntry subentryAttrs = new DefaultServerEntry( schemaManager, dn );
        Iterator<String> list = subentryCache.nameIterator();

        while ( list.hasNext() )
        {
            String subentryDnStr = list.next();
            DN subentryDn = new DN( subentryDnStr );
            DN apDn = ( DN ) subentryDn.clone();
            apDn.remove( apDn.size() - 1 );
            Subentry subentry = subentryCache.getSubentry( subentryDnStr );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();

            if ( evaluator.evaluate( ss, apDn, dn, entryAttrs ) )
            {
                EntryAttribute operational;

                if ( subentry.isAccessControlSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT,
                            schemaManager.lookupAttributeTypeRegistry( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }
                if ( subentry.isSchemaSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, schemaManager
                            .lookupAttributeTypeRegistry( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }
                if ( subentry.isCollectiveSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT,
                            schemaManager.lookupAttributeTypeRegistry( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }
                if ( subentry.isTriggerSubentry() )
                {
                    operational = subentryAttrs.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultServerAttribute( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT,
                            schemaManager.lookupAttributeTypeRegistry( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }
            }
        }

        return subentryAttrs;
    }


    public void add( NextInterceptor next, AddOperationContext addContext ) throws Exception
    {
        DN name = addContext.getDn();
        ClonedServerEntry entry = addContext.getEntry();

        EntryAttribute objectClasses = entry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            // get the name of the administrative point and its administrativeRole attributes
            DN apName = ( DN ) name.clone();
            apName.remove( name.size() - 1 );
            ServerEntry ap = addContext.lookup( apName, ByPassConstants.LOOKUP_BYPASS );
            EntryAttribute administrativeRole = ap.get( "administrativeRole" );

            // check that administrativeRole has something valid in it for us
            if ( administrativeRole == null || administrativeRole.size() <= 0 )
            {
                throw new LdapNoSuchAttributeException( I18n.err( I18n.ERR_306, apName ) );
            }

            /* ----------------------------------------------------------------
             * Build the set of operational attributes to be injected into
             * entries that are contained within the subtree repesented by this
             * new subentry.  In the process we make sure the proper roles are
             * supported by the administrative point to allow the addition of
             * this new subentry.
             * ----------------------------------------------------------------
             */
            Subentry subentry = new Subentry();
            subentry.setTypes( getSubentryTypes( entry ) );
            ServerEntry operational = getSubentryOperatationalAttributes( name, subentry );

            /* ----------------------------------------------------------------
             * Parse the subtreeSpecification of the subentry and add it to the
             * SubtreeSpecification cache.  If the parse succeeds we continue
             * to add the entry to the DIT.  Thereafter we search out entries
             * to modify the subentry operational attributes of.
             * ----------------------------------------------------------------
             */
            String subtree = entry.get( SchemaConstants.SUBTREE_SPECIFICATION_AT ).getString();
            SubtreeSpecification ss;

            try
            {
                ss = ssParser.parse( subtree );
            }
            catch ( Exception e )
            {
                String msg = I18n.err( I18n.ERR_307, name.getName() );
                LOG.warn( msg );
                throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, msg );
            }

            subentryCache.setSubentry( name.getNormName(), ss, getSubentryTypes( entry ) );

            next.add( addContext );

            /* ----------------------------------------------------------------
             * Find the baseDn for the subentry and use that to search the tree
             * while testing each entry returned for inclusion within the
             * subtree of the subentry's subtreeSpecification.  All included
             * entries will have their operational attributes merged with the
             * operational attributes calculated above.
             * ----------------------------------------------------------------
             */
            DN baseDn = ( DN ) apName.clone();
            baseDn.addAll( ss.getBase() );

            ExprNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT_OID ); // (objectClass=*)
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( addContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = candidate.getDn();
                dn.normalize( schemaManager.getNormalizerMapping() );

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( addContext.getSession(), dn, 
                        getOperationalModsForAdd( candidate, operational ) ) );
                }
            }

            // TODO why are we doing this here if we got the entry from the 
            // opContext in the first place - got to look into this 
            addContext.setEntry( entry );
        }
        else
        {
            Iterator<String> list = subentryCache.nameIterator();

            while ( list.hasNext() )
            {
                String subentryDnStr = list.next();
                DN subentryDn = new DN( subentryDnStr );
                DN apDn = ( DN ) subentryDn.clone();
                apDn.remove( apDn.size() - 1 );
                Subentry subentry = subentryCache.getSubentry( subentryDnStr );
                SubtreeSpecification ss = subentry.getSubtreeSpecification();

                if ( evaluator.evaluate( ss, apDn, name, entry ) )
                {
                    EntryAttribute operational;

                    if ( subentry.isAccessControlSubentry() )
                    {
                        operational = entry.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( schemaManager
                                .lookupAttributeTypeRegistry( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.getNormName() );
                    }

                    if ( subentry.isSchemaSubentry() )
                    {
                        operational = entry.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( schemaManager
                                .lookupAttributeTypeRegistry( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.getNormName() );
                    }

                    if ( subentry.isCollectiveSubentry() )
                    {
                        operational = entry.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( schemaManager
                                .lookupAttributeTypeRegistry( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.getNormName() );
                    }

                    if ( subentry.isTriggerSubentry() )
                    {
                        operational = entry.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT );

                        if ( operational == null )
                        {
                            operational = new DefaultServerAttribute( schemaManager
                                .lookupAttributeTypeRegistry( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) );
                            entry.put( operational );
                        }

                        operational.add( subentryDn.getNormName() );
                    }
                }
            }

            // TODO why are we doing this here if we got the entry from the 
            // opContext in the first place - got to look into this 
            addContext.setEntry( entry );

            next.add( addContext );
        }
    }


    // -----------------------------------------------------------------------
    // Methods dealing subentry deletion
    // -----------------------------------------------------------------------

    public void delete( NextInterceptor next, DeleteOperationContext opContext ) throws Exception
    {
        DN name = opContext.getDn();
        ServerEntry entry = opContext.lookup( name, ByPassConstants.LOOKUP_BYPASS );
        EntryAttribute objectClasses = entry.get( objectClassType );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            SubtreeSpecification ss = subentryCache.removeSubentry( name.getNormName() ).getSubtreeSpecification();
            next.delete( opContext );

            /* ----------------------------------------------------------------
             * Find the baseDn for the subentry and use that to search the tree
             * for all entries included by the subtreeSpecification.  Then we
             * check the entry for subentry operational attribute that contain
             * the DN of the subentry.  These are the subentry operational
             * attributes we remove from the entry in a modify operation.
             * ----------------------------------------------------------------
             */
            DN apName = ( DN ) name.clone();
            apName.remove( name.size() - 1 );
            DN baseDn = ( DN ) apName.clone();
            baseDn.addAll( ss.getBase() );

            ExprNode filter = new PresenceNode( schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = new DN( candidate.getDn() );
                dn.normalize( schemaManager.getNormalizerMapping() );

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), dn, 
                        getOperationalModsForRemove( name, candidate ) ) );
                }
            }
        }
        else
        {
            next.delete( opContext );
        }
    }


    // -----------------------------------------------------------------------
    // Methods dealing subentry name changes
    // -----------------------------------------------------------------------

    /**
     * Checks to see if an entry being renamed has a descendant that is an
     * administrative point.
     *
     * @param name the name of the entry which is used as the search base
     * @return true if name is an administrative point or one of its descendants
     * are, false otherwise
     * @throws Exception if there are errors while searching the directory
     */
    private boolean hasAdministrativeDescendant( OperationContext opContext, DN name ) throws Exception
    {
        ExprNode filter = new PresenceNode( "administrativeRole" );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), name,
            filter, controls );
        searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
        
        EntryFilteringCursor aps = nexus.search( searchOperationContext );

        if ( aps.next() )
        {
            aps.close();
            return true;
        }

        return false;
    }


    private List<Modification> getModsOnEntryRdnChange( DN oldName, DN newName, ServerEntry entry )
        throws Exception
    {
        List<Modification> modList = new ArrayList<Modification>();

        /*
         * There are two different situations warranting action.  Firt if
         * an ss evalutating to true with the old name no longer evalutates
         * to true with the new name.  This would be caused by specific chop
         * exclusions that effect the new name but did not effect the old
         * name. In this case we must remove subentry operational attribute
         * values associated with the dn of that subentry.
         *
         * In the second case an ss selects the entry with the new name when
         * it did not previously with the old name.  Again this situation
         * would be caused by chop exclusions. In this case we must add subentry
         * operational attribute values with the dn of this subentry.
         */
        Iterator<String> subentries = subentryCache.nameIterator();

        while ( subentries.hasNext() )
        {
            String subentryDn = subentries.next();
            DN apDn = new DN( subentryDn );
            apDn.remove( apDn.size() - 1 );
            SubtreeSpecification ss = subentryCache.getSubentry( subentryDn ).getSubtreeSpecification();
            boolean isOldNameSelected = evaluator.evaluate( ss, apDn, oldName, entry );
            boolean isNewNameSelected = evaluator.evaluate( ss, apDn, newName, entry );

            if ( isOldNameSelected == isNewNameSelected )
            {
                continue;
            }

            // need to remove references to the subentry
            if ( isOldNameSelected && !isNewNameSelected )
            {
                for ( String aSUBENTRY_OPATTRS : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
                    EntryAttribute opAttr = entry.get( aSUBENTRY_OPATTRS );

                    if ( opAttr != null )
                    {
                        opAttr = opAttr.clone();
                        opAttr.remove( subentryDn );

                        if ( opAttr.size() < 1 )
                        {
                            op = ModificationOperation.REMOVE_ATTRIBUTE;
                        }

                        modList.add( new ServerModification( op, opAttr ) );
                    }
                }
            }
            // need to add references to the subentry
            else if ( isNewNameSelected && !isOldNameSelected )
            {
                for ( String aSUBENTRY_OPATTRS : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.ADD_ATTRIBUTE;
                    EntryAttribute opAttr = new DefaultServerAttribute( aSUBENTRY_OPATTRS, schemaManager
                        .lookupAttributeTypeRegistry( aSUBENTRY_OPATTRS ) );
                    opAttr.add( subentryDn );
                    modList.add( new ServerModification( op, opAttr ) );
                }
            }
        }

        return modList;
    }


    public void rename( NextInterceptor next, RenameOperationContext opContext ) throws Exception
    {
        DN name = opContext.getDn();

        ServerEntry entry = (ServerEntry)opContext.getEntry().getClonedEntry();

        EntryAttribute objectClasses = entry.get( objectClassType );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            // @Todo To be reviewed !!!
            Subentry subentry = subentryCache.getSubentry( name.getNormName() );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            DN apName = ( DN ) name.clone();
            apName.remove( apName.size() - 1 );
            DN baseDn = ( DN ) apName.clone();
            baseDn.addAll( ss.getBase() );
            DN newName = ( DN ) name.clone();
            newName.remove( newName.size() - 1 );

            newName.add( opContext.getNewRdn() );

            String newNormName = newName.getNormName();
            subentryCache.setSubentry( newNormName, ss, subentry.getTypes() );
            next.rename( opContext );

            subentry = subentryCache.getSubentry( newNormName );
            ExprNode filter = new PresenceNode( schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );
            
            SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = candidate.getDn();
                dn.normalize( schemaManager.getNormalizerMapping() );


                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), dn, 
                        getOperationalModsForReplace( name, newName, subentry, candidate ) ) );
                }
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( opContext, name ) )
            {
                String msg = I18n.err( I18n.ERR_308 );
                LOG.warn( msg );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, msg );
            }

            next.rename( opContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            DN newName = opContext.getNewDn();

            List<Modification> mods = getModsOnEntryRdnChange( name, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( opContext.getSession(), newName, mods ) );
            }
        }
    }


    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext opContext ) throws Exception
    {
        DN oriChildName = opContext.getDn();
        DN parent = opContext.getParent();

        ServerEntry entry = opContext.lookup( oriChildName, ByPassConstants.LOOKUP_BYPASS );

        EntryAttribute objectClasses = entry.get( objectClassType );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            Subentry subentry = subentryCache.getSubentry( oriChildName.getNormName() );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            DN apName = ( DN ) oriChildName.clone();
            apName.remove( apName.size() - 1 );
            DN baseDn = ( DN ) apName.clone();
            baseDn.addAll( ss.getBase() );
            DN newName = ( DN ) parent.clone();
            newName.remove( newName.size() - 1 );

            newName.add( opContext.getNewRdn() );

            String newNormName = newName.getNormName();
            subentryCache.setSubentry( newNormName, ss, subentry.getTypes() );
            next.moveAndRename( opContext );

            subentry = subentryCache.getSubentry( newNormName );

            ExprNode filter = new PresenceNode( schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = candidate.getDn();
                dn.normalize( schemaManager.getNormalizerMapping() );

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), dn, 
                        getOperationalModsForReplace( oriChildName, newName, subentry, candidate ) ) );
                }
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( opContext, oriChildName ) )
            {
                String msg = I18n.err( I18n.ERR_308 );
                LOG.warn( msg );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, msg );
            }

            next.moveAndRename( opContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            DN newName = ( DN ) parent.clone();
            newName.add( opContext.getNewRdn() );
            newName.normalize( schemaManager.getNormalizerMapping() );
            List<Modification> mods = getModsOnEntryRdnChange( oriChildName, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( opContext.getSession(), newName, mods ) );
            }
        }
    }


    public void move( NextInterceptor next, MoveOperationContext opContext ) throws Exception
    {
        DN oriChildName = opContext.getDn();
        DN newParentName = opContext.getParent();

        ServerEntry entry = opContext.lookup( oriChildName, ByPassConstants.LOOKUP_BYPASS );

        EntryAttribute objectClasses = entry.get( SchemaConstants.OBJECT_CLASS_AT );

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
        {
            Subentry subentry = subentryCache.getSubentry( oriChildName.getNormName() );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            DN apName = ( DN ) oriChildName.clone();
            apName.remove( apName.size() - 1 );
            DN baseDn = ( DN ) apName.clone();
            baseDn.addAll( ss.getBase() );
            DN newName = ( DN ) newParentName.clone();
            newName.remove( newName.size() - 1 );
            newName.add( newParentName.get( newParentName.size() - 1 ) );

            String newNormName = newName.getNormName();
            subentryCache.setSubentry( newNormName, ss, subentry.getTypes() );
            next.move( opContext );

            subentry = subentryCache.getSubentry( newNormName );

            ExprNode filter = new PresenceNode( SchemaConstants.OBJECT_CLASS_AT );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = candidate.getDn();
                dn.normalize( schemaManager.getNormalizerMapping() );

                if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), dn, 
                        getOperationalModsForReplace( oriChildName, newName, subentry, candidate ) ) );
                }
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( opContext, oriChildName ) )
            {
                String msg = I18n.err( I18n.ERR_308 );
                LOG.warn( msg );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, msg );
            }

            next.move( opContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            DN newName = ( DN ) newParentName.clone();
            newName.add( oriChildName.get( oriChildName.size() - 1 ) );
            List<Modification> mods = getModsOnEntryRdnChange( oriChildName, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( opContext.getSession(), newName, mods ) );
            }
        }
    }


    // -----------------------------------------------------------------------
    // Methods dealing subentry modification
    // -----------------------------------------------------------------------

    private int getSubentryTypes( ServerEntry entry, List<Modification> mods ) throws Exception
    {
        EntryAttribute ocFinalState = entry.get( SchemaConstants.OBJECT_CLASS_AT ).clone();

        for ( Modification mod : mods )
        {
            if ( mod.getAttribute().getId().equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) )
            {
                switch ( mod.getOperation() )
                {
                    case ADD_ATTRIBUTE:
                        for ( Value<?> value : mod.getAttribute() )
                        {
                            ocFinalState.add( value.getString() );
                        }

                        break;

                    case REMOVE_ATTRIBUTE:
                        for ( Value<?> value : mod.getAttribute() )
                        {
                            ocFinalState.remove( value.getString() );
                        }

                        break;

                    case REPLACE_ATTRIBUTE:
                        ocFinalState = mod.getAttribute();
                        break;
                }
            }
        }

        ServerEntry attrs = new DefaultServerEntry( schemaManager, DN.EMPTY_DN );
        attrs.put( ocFinalState );
        return getSubentryTypes( attrs );
    }


    public void modify( NextInterceptor next, ModifyOperationContext opContext ) throws Exception
    {
        DN name = opContext.getDn();
        List<Modification> mods = opContext.getModItems();

        ServerEntry entry = opContext.lookup( name, ByPassConstants.LOOKUP_BYPASS );

        ServerEntry oldEntry = ( ServerEntry ) entry.clone();
        EntryAttribute objectClasses = entry.get( objectClassType );
        boolean isSubtreeSpecificationModification = false;
        Modification subtreeMod = null;

        for ( Modification mod : mods )
        {
            if ( SchemaConstants.SUBTREE_SPECIFICATION_AT.equalsIgnoreCase( mod.getAttribute().getId() ) )
            {
                isSubtreeSpecificationModification = true;
                subtreeMod = mod;
            }
        }

        if ( objectClasses.contains( SchemaConstants.SUBENTRY_OC ) && isSubtreeSpecificationModification )
        {
            SubtreeSpecification ssOld = subentryCache.removeSubentry( name.getNormName() ).getSubtreeSpecification();
            SubtreeSpecification ssNew;

            try
            {
                ssNew = ssParser.parse( subtreeMod.getAttribute().getString() );
            }
            catch ( Exception e )
            {
                String msg = I18n.err( I18n.ERR_71 );
                LOG.error( msg, e );
                throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, msg );
            }

            subentryCache.setSubentry( name.getNormName(), ssNew, getSubentryTypes( entry, mods ) );
            next.modify( opContext );

            // search for all entries selected by the old SS and remove references to subentry
            DN apName = ( DN ) name.clone();
            apName.remove( apName.size() - 1 );
            DN oldBaseDn = ( DN ) apName.clone();
            oldBaseDn.addAll( ssOld.getBase() );
            ExprNode filter = new PresenceNode( schemaManager.getAttributeTypeRegistry().getOidByName( SchemaConstants.OBJECT_CLASS_AT ) );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), oldBaseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = candidate.getDn();
                dn.normalize( schemaManager.getNormalizerMapping() );

                if ( evaluator.evaluate( ssOld, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), dn, 
                        getOperationalModsForRemove( name, candidate ) ) );
                }
            }

            // search for all selected entries by the new SS and add references to subentry
            Subentry subentry = subentryCache.getSubentry( name.getNormName() );
            ServerEntry operational = getSubentryOperatationalAttributes( name, subentry );
            DN newBaseDn = ( DN ) apName.clone();
            newBaseDn.addAll( ssNew.getBase() );
            
            searchOperationContext = new SearchOperationContext( opContext.getSession(), newBaseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );
            
            subentries = nexus.search( searchOperationContext );
            
            while ( subentries.next() )
            {
                ServerEntry candidate = subentries.get();
                DN dn = candidate.getDn();
                dn.normalize( schemaManager.getNormalizerMapping() );

                if ( evaluator.evaluate( ssNew, apName, dn, candidate ) )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), dn, 
                        getOperationalModsForAdd( candidate, operational ) ) );
                }
            }
        }
        else
        {
            next.modify( opContext );

            if ( !objectClasses.contains( SchemaConstants.SUBENTRY_OC ) )
            {
                ServerEntry newEntry = opContext.lookup( name, ByPassConstants.LOOKUP_BYPASS );

                List<Modification> subentriesOpAttrMods = getModsOnEntryModification( name, oldEntry, newEntry );

                if ( subentriesOpAttrMods.size() > 0 )
                {
                    nexus.modify( new ModifyOperationContext( opContext.getSession(), name, subentriesOpAttrMods ) );
                }
            }
        }
    }


    // -----------------------------------------------------------------------
    // Utility Methods
    // -----------------------------------------------------------------------

    private List<Modification> getOperationalModsForReplace( DN oldName, DN newName, Subentry subentry,
        ServerEntry entry ) throws Exception
    {
        List<Modification> modList = new ArrayList<Modification>();

        EntryAttribute operational;

        if ( subentry.isAccessControlSubentry() )
        {
            operational = entry.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT, schemaManager
                    .lookupAttributeTypeRegistry( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        if ( subentry.isSchemaSubentry() )
        {
            operational = entry.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, schemaManager
                    .lookupAttributeTypeRegistry( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        if ( subentry.isCollectiveSubentry() )
        {
            operational = entry.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT,
                    schemaManager.lookupAttributeTypeRegistry( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        if ( subentry.isTriggerSubentry() )
        {
            operational = entry.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ).clone();

            if ( operational == null )
            {
                operational = new DefaultServerAttribute( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT, schemaManager
                    .lookupAttributeTypeRegistry( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) );
                operational.add( newName.toString() );
            }
            else
            {
                operational.remove( oldName.toString() );
                operational.add( newName.toString() );
            }

            modList.add( new ServerModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }

        return modList;
    }


    /**
     * Gets the subschema operational attributes to be added to or removed from
     * an entry selected by a subentry's subtreeSpecification.
     *
     * @param name the normalized distinguished name of the subentry (the value of op attrs)
     * @param subentry the subentry to get attributes from
     * @return the set of attributes to be added or removed from entries
     */
    private ServerEntry getSubentryOperatationalAttributes( DN name, Subentry subentry ) throws Exception
    {
        ServerEntry operational = new DefaultServerEntry( schemaManager, name );

        if ( subentry.isAccessControlSubentry() )
        {
            if ( operational.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ) == null )
            {
                operational.put( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT, name.getNormName() );
            }
            else
            {
                operational.get( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT ).add( name.getNormName() );
            }
        }
        if ( subentry.isSchemaSubentry() )
        {
            if ( operational.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ) == null )
            {
                operational.put( SchemaConstants.SUBSCHEMA_SUBENTRY_AT, name.getNormName() );
            }
            else
            {
                operational.get( SchemaConstants.SUBSCHEMA_SUBENTRY_AT ).add( name.getNormName() );
            }
        }
        if ( subentry.isCollectiveSubentry() )
        {
            if ( operational.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ) == null )
            {
                operational.put( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT, name.getNormName() );
            }
            else
            {
                operational.get( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT ).add( name.getNormName() );
            }
        }
        if ( subentry.isTriggerSubentry() )
        {
            if ( operational.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ) == null )
            {
                operational.put( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT, name.getNormName() );
            }
            else
            {
                operational.get( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT ).add( name.getNormName() );
            }
        }

        return operational;
    }


    /**
     * Calculates the subentry operational attributes to remove from a candidate
     * entry selected by a subtreeSpecification.  When we remove a subentry we
     * must remove the operational attributes in the entries that were once selected
     * by the subtree specification of that subentry.  To do so we must perform
     * a modify operation with the set of modifications to perform.  This method
     * calculates those modifications.
     *
     * @param subentryDn the distinguished name of the subentry
     * @param candidate the candidate entry to removed from the
     * @return the set of modifications required to remove an entry's reference to
     * a subentry
     */
    private List<Modification> getOperationalModsForRemove( DN subentryDn, ServerEntry candidate )
        throws Exception
    {
        List<Modification> modList = new ArrayList<Modification>();
        String dn = subentryDn.getNormName();

        for ( String opAttrId : SUBENTRY_OPATTRS )
        {
            EntryAttribute opAttr = candidate.get( opAttrId );

            if ( ( opAttr != null ) && opAttr.contains( dn ) )
            {
                AttributeType attributeType = schemaManager.lookupAttributeTypeRegistry( opAttrId );
                EntryAttribute attr = new DefaultServerAttribute( opAttrId, attributeType, dn );
                modList.add( new ServerModification( ModificationOperation.REMOVE_ATTRIBUTE, attr ) );
            }
        }

        return modList;
    }


    /**
     * Calculates the subentry operational attributes to add or replace from
     * a candidate entry selected by a subtree specification.  When a subentry
     * is added or it's specification is modified some entries must have new
     * operational attributes added to it to point back to the associated
     * subentry.  To do so a modify operation must be performed on entries
     * selected by the subtree specification.  This method calculates the
     * modify operation to be performed on the entry.
     *
     * @param entry the entry being modified
     * @param operational the set of operational attributes supported by the AP
     * of the subentry
     * @return the set of modifications needed to update the entry
     * @throws Exception if there are probelms accessing modification items
     */
    public List<Modification> getOperationalModsForAdd( ServerEntry entry, ServerEntry operational )
        throws Exception
    {
        List<Modification> modList = new ArrayList<Modification>();

        for ( AttributeType attributeType : operational.getAttributeTypes() )
        {
            ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
            EntryAttribute result = new DefaultServerAttribute( attributeType );
            EntryAttribute opAttrAdditions = operational.get( attributeType );
            EntryAttribute opAttrInEntry = entry.get( attributeType );

            for ( Value<?> value : opAttrAdditions )
            {
                result.add( value );
            }

            if ( opAttrInEntry != null && opAttrInEntry.size() > 0 )
            {
                for ( Value<?> value : opAttrInEntry )
                {
                    result.add( value );
                }
            }
            else
            {
                op = ModificationOperation.ADD_ATTRIBUTE;
            }

            modList.add( new ServerModification( op, result ) );
        }

        return modList;
    }

    /**
     * SearchResultFilter used to filter out subentries based on objectClass values.
     */
    public class HideSubentriesFilter implements EntryFilter
    {
        public boolean accept( SearchingOperationContext operation, ClonedServerEntry entry )
            throws Exception
        {
            String dn = entry.getDn().getNormName();

            // see if we can get a match without normalization
            if ( subentryCache.hasSubentry( dn ) )
            {
                return false;
            }

            // see if we can use objectclass if present
            EntryAttribute objectClasses = entry.get( SchemaConstants.OBJECT_CLASS_AT );

            if ( objectClasses != null )
            {
                return !objectClasses.contains( SchemaConstants.SUBENTRY_OC );
            }

            DN ndn = new DN( dn );
            ndn.normalize( schemaManager.getNormalizerMapping() );
            String normalizedDn = ndn.getNormName();
            return !subentryCache.hasSubentry( normalizedDn );
        }
    }

    /**
     * SearchResultFilter used to filter out normal entries but shows subentries based on 
     * objectClass values.
     */
    public class HideEntriesFilter implements EntryFilter
    {
        public boolean accept( SearchingOperationContext operation, ClonedServerEntry entry )
            throws Exception
        {
            String dn = entry.getDn().getNormName();

            // see if we can get a match without normalization
            if ( subentryCache.hasSubentry( dn ) )
            {
                return true;
            }

            // see if we can use objectclass if present
            EntryAttribute objectClasses = entry.get( SchemaConstants.OBJECT_CLASS_AT );

            if ( objectClasses != null )
            {
                return objectClasses.contains( SchemaConstants.SUBENTRY_OC );
            }

            DN ndn = new DN( dn );
            ndn.normalize( schemaManager.getNormalizerMapping() );
            return subentryCache.hasSubentry( ndn.getNormName() );
        }
    }


    private List<Modification> getModsOnEntryModification( DN name, ServerEntry oldEntry, ServerEntry newEntry )
        throws Exception
    {
        List<Modification> modList = new ArrayList<Modification>();

        Iterator<String> subentries = subentryCache.nameIterator();

        while ( subentries.hasNext() )
        {
            String subentryDn = subentries.next();
            DN apDn = new DN( subentryDn );
            apDn.remove( apDn.size() - 1 );
            SubtreeSpecification ss = subentryCache.getSubentry( subentryDn ).getSubtreeSpecification();
            boolean isOldEntrySelected = evaluator.evaluate( ss, apDn, name, oldEntry );
            boolean isNewEntrySelected = evaluator.evaluate( ss, apDn, name, newEntry );

            if ( isOldEntrySelected == isNewEntrySelected )
            {
                continue;
            }

            // need to remove references to the subentry
            if ( isOldEntrySelected && !isNewEntrySelected )
            {
                for ( String aSUBENTRY_OPATTRS : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
                    EntryAttribute opAttr = oldEntry.get( aSUBENTRY_OPATTRS );

                    if ( opAttr != null )
                    {
                        opAttr = opAttr.clone();
                        opAttr.remove( subentryDn );

                        if ( opAttr.size() < 1 )
                        {
                            op = ModificationOperation.REMOVE_ATTRIBUTE;
                        }

                        modList.add( new ServerModification( op, opAttr ) );
                    }
                }
            }
            // need to add references to the subentry
            else if ( isNewEntrySelected && !isOldEntrySelected )
            {
                for ( String attribute : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.ADD_ATTRIBUTE;
                    AttributeType type = schemaManager.lookupAttributeTypeRegistry( attribute );
                    EntryAttribute opAttr = new DefaultServerAttribute( attribute, type );
                    opAttr.add( subentryDn );
                    modList.add( new ServerModification( op, opAttr ) );
                }
            }
        }

        return modList;
    }

}
