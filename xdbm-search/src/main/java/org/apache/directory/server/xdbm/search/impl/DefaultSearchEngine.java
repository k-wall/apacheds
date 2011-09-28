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
package org.apache.directory.server.xdbm.search.impl;


import javax.naming.directory.SearchControls;

import org.apache.directory.server.xdbm.EmptyIndexCursor;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.IndexCursor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.SingletonIndexCursor;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.search.Evaluator;
import org.apache.directory.server.xdbm.search.Optimizer;
import org.apache.directory.server.xdbm.search.SearchEngine;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.ScopeNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.name.DN;


/**
 * Given a search filter and a scope the search engine identifies valid
 * candidate entries returning their ids.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $
 */
public class DefaultSearchEngine<ID> implements SearchEngine<ServerEntry, ID>
{
    /** the Optimizer used by this DefaultSearchEngine */
    private final Optimizer optimizer;
    /** the Database this DefaultSearchEngine operates on */
    private final Store<ServerEntry, ID> db;
    /** creates Cursors over entries satisfying filter expressions */
    private final CursorBuilder<ID> cursorBuilder;
    /** creates evaluators which check to see if candidates satisfy a filter expression */
    private final EvaluatorBuilder<ID> evaluatorBuilder;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a DefaultSearchEngine for searching a Database without setting
     * up the database.
     * @param db the btree based partition
     * @param cursorBuilder an expression cursor builder
     * @param evaluatorBuilder an expression evaluator builder
     * @param optimizer an optimizer to use during search
     */
    public DefaultSearchEngine( Store<ServerEntry, ID> db, CursorBuilder<ID> cursorBuilder,
        EvaluatorBuilder<ID> evaluatorBuilder, Optimizer optimizer )
    {
        this.db = db;
        this.optimizer = optimizer;
        this.cursorBuilder = cursorBuilder;
        this.evaluatorBuilder = evaluatorBuilder;
    }


    /**
     * Gets the optimizer for this DefaultSearchEngine.
     *
     * @return the optimizer
     */
    public Optimizer getOptimizer()
    {
        return optimizer;
    }


    /**
     * @see SearchEngine#cursor(DN, AliasDerefMode, ExprNode, SearchControls)
     */
    public IndexCursor<ID, ServerEntry, ID> cursor( DN base, AliasDerefMode aliasDerefMode, ExprNode filter,
        SearchControls searchCtls ) throws Exception
    {
        DN effectiveBase;
        ID baseId = db.getEntryId( base.getNormName() );

        // Check that we have an entry, otherwise we can immediately get out
        if ( baseId == null )
        {
            // The entry is not found : ciao !
            return new EmptyIndexCursor<ID, ServerEntry, ID>();
        }

        String aliasedBase = db.getAliasIndex().reverseLookup( baseId );

        // --------------------------------------------------------------------
        // Determine the effective base with aliases
        // --------------------------------------------------------------------

        /*
         * If the base is not an alias or if alias dereferencing does not
         * occur on finding the base then we set the effective base to the
         * given base.
         */
        if ( ( null == aliasedBase ) || !aliasDerefMode.isDerefFindingBase() )
        {
            effectiveBase = base;
        }

        /*
         * If the base is an alias and alias dereferencing does occur on
         * finding the base then we set the effective base to the alias target
         * got from the alias index.
         */
        else
        {
            effectiveBase = new DN( aliasedBase );
        }

        // --------------------------------------------------------------------
        // Specifically Handle Object Level Scope
        // --------------------------------------------------------------------

        if ( searchCtls.getSearchScope() == SearchControls.OBJECT_SCOPE )
        {
            ID effectiveBaseId = baseId;
            if ( effectiveBase != base )
            {
                effectiveBaseId = db.getEntryId( effectiveBase.getNormName() );
            }

            IndexEntry<ID, ServerEntry, ID> indexEntry = new ForwardIndexEntry<ID, ServerEntry, ID>();
            indexEntry.setId( effectiveBaseId );
            optimizer.annotate( filter );
            Evaluator<? extends ExprNode, ServerEntry, ID> evaluator = evaluatorBuilder.build( filter );

            if ( evaluator.evaluate( indexEntry ) )
            {
                return new SingletonIndexCursor<ID, ServerEntry, ID>( indexEntry );
            }
            else
            {
                return new EmptyIndexCursor<ID, ServerEntry, ID>();
            }
        }

        // Add the scope node using the effective base to the filter
        BranchNode root = new AndNode();
        ExprNode node = new ScopeNode( aliasDerefMode, effectiveBase.getNormName(), SearchScope.getSearchScope( searchCtls
            .getSearchScope() ) );
        root.getChildren().add( node );
        root.getChildren().add( filter );

        // Annotate the node with the optimizer and return search enumeration.
        optimizer.annotate( root );
        return ( IndexCursor<ID, ServerEntry, ID> ) cursorBuilder.build( root );
    }


    /**
     * @see SearchEngine#evaluator(ExprNode)
     */
    public Evaluator<? extends ExprNode, ServerEntry, ID> evaluator( ExprNode filter ) throws Exception
    {
        return evaluatorBuilder.build( filter );
    }
}
