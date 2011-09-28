package org.apache.directory.server.core.schema;


import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.ObjectClass;
import org.apache.directory.shared.ldap.schema.registries.Schema;


public interface SchemaPartitionDao
{

    Map<String, Schema> getSchemas() throws Exception;


    Set<String> getSchemaNames() throws Exception;


    Schema getSchema( String schemaName ) throws Exception;


    boolean hasMatchingRule( String oid ) throws Exception;


    boolean hasAttributeType( String oid ) throws Exception;


    boolean hasObjectClass( String oid ) throws Exception;


    boolean hasSyntax( String oid ) throws Exception;


    boolean hasSyntaxChecker( String oid ) throws Exception;


    /**
     * Given the non-normalized name (alias) or the OID for a schema entity.  This 
     * method finds the schema under which that entity is located. 
     * 
     * NOTE: this method presumes that all alias names across schemas are unique.  
     * This should be the case for LDAP but this can potentially be violated so 
     * we should make sure this is a unique name.
     * 
     * @param entityName one of the names of the entity or it's numeric id
     * @return the name of the schema that contains that entity or null if no entity with 
     * that alias name exists
     * @throws NamingException if more than one entity has the name, or if there 
     * are underlying data access problems
     */
    String findSchema( String entityName ) throws Exception;


    DN findDn( String entityName ) throws Exception;


    /**
     * Given the non-normalized name (alias) or the OID for a schema entity.  This 
     * method finds the entry of the schema entity. 
     * 
     * NOTE: this method presumes that all alias names across schemas are unique.  
     * This should be the case for LDAP but this can potentially be violated so 
     * we should make sure this is a unique name.
     * 
     * @param entityName one of the names of the entity or it's numeric id
     * @return the search result for the entity or null if no such entity exists with 
     * that alias or numeric oid
     * @throws NamingException if more than one entity has the name, or if there 
     * are underlying data access problems
     */
    ServerEntry find( String entityName ) throws Exception;


    /**
     * Enables a schema by removing it's m-disabled attribute if present.
     * 
     * NOTE:
     * This is a write operation and great care must be taken to make sure it
     * is used in a limited capacity.  This method is called in two places 
     * currently.  
     * 
     * (1) Within the initialization sequence to enable schemas required
     *     for the correct operation of indices in other partitions.
     * (2) Within the partition schema loader to auto enable schemas that are
     *     depended on by other schemas which are enabled.
     * 
     * In both cases, the modifier is effectively the administrator since the 
     * server is performing the operation directly or on behalf of a user.  In 
     * case (1) during intialization there is no other user involved so naturally
     * the modifier is the administrator.  In case (2) when a user enables a 
     * schema with a dependency that is not enabled the server enables that 
     * dependency on behalf of the user.  Again effectively it is the server that
     * is modifying the schema entry and hence the admin is the modifier.
     * 
     * No need to worry about a lack of replication propagation in both cases.  In 
     * case (1) all replicas will enable these schemas anyway on startup.  In case
     * (2) the original operation that enabled the schema depending on the on that
     * enableSchema() is called for itself will be replicated.  Hence the same chain 
     * reaction will occur in a replica.
     * 
     * @param schemaName the name of the schema to enable
     * @throws NamingException if there is a problem updating the schema entry
     */
    void enableSchema( String schemaName ) throws Exception;


    /**
     * Returns the set of matchingRules and attributeTypes which depend on the 
     * provided syntax.
     *
     * @param numericOid the numeric identifier for the entity
     * @return the set of matchingRules and attributeTypes depending on a syntax
     * @throws NamingException if the dao fails to perform search operations
     */
    Set<ServerEntry> listSyntaxDependents( String numericOid ) throws Exception;


    Set<ServerEntry> listMatchingRuleDependents( MatchingRule mr ) throws Exception;


    EntryFilteringCursor listAllNames() throws Exception;


    Set<ServerEntry> listAttributeTypeDependents( AttributeType at ) throws Exception;


    /**
     * Lists the SearchResults of metaSchema objects that depend on a schema.
     * 
     * @param schemaName the name of the schema to search for dependees
     * @return a set of SearchResults over the schemas whose m-dependency attribute contains schemaName
     * @throws NamingException if there is a problem while searching the schema partition
     */
    Set<ServerEntry> listSchemaDependents( String schemaName ) throws Exception;


    /**
     * Lists the SearchResults of metaSchema objects that depend on a schema.
     * 
     * @param schemaName the name of the schema to search for dependencies
     * @return a set of SearchResults over the schemas whose m-dependency attribute contains schemaName
     * @throws NamingException if there is a problem while searching the schema partition
     */
    Set<ServerEntry> listEnabledSchemaDependents( String schemaName ) throws Exception;


    Set<ServerEntry> listObjectClassDependents( ObjectClass oc ) throws Exception;
}