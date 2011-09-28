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


import java.util.ArrayList;
import java.util.List;

import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.search.Evaluator;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.ApproximateNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.GreaterEqNode;
import org.apache.directory.shared.ldap.filter.LessEqNode;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.ScopeNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * Top level filter expression evaluator builder implemenation.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 927146 $
 */
public class EvaluatorBuilder<ID>
{
    private final Store<ServerEntry, ID> db;
    private final SchemaManager schemaManager;


    /**
     * Creates a top level Evaluator where leaves are delegated to a leaf node
     * evaluator which will be created.
     *
     * @param db the database this evaluator operates upon
     * @param registries the schema registries
     * @throws Exception failure to access db or lookup schema in registries
     */
    public EvaluatorBuilder( Store<ServerEntry, ID> db, SchemaManager schemaManager ) throws Exception
    {
        this.db = db;
        this.schemaManager = schemaManager;
    }


    public <T> Evaluator<? extends ExprNode, ServerEntry, ID> build( ExprNode node ) throws Exception
    {
        switch ( node.getAssertionType() )
        {
            /* ---------- LEAF NODE HANDLING ---------- */

            case APPROXIMATE:
                return new ApproximateEvaluator<T, ID>( ( ApproximateNode<T> ) node, db, schemaManager );

            case EQUALITY:
                return new EqualityEvaluator<T, ID>( ( EqualityNode<T> ) node, db, schemaManager );

            case GREATEREQ:
                return new GreaterEqEvaluator<T, ID>( ( GreaterEqNode<T> ) node, db, schemaManager );

            case LESSEQ:
                return new LessEqEvaluator<T, ID>( ( LessEqNode<T> ) node, db, schemaManager );

            case PRESENCE:
                return new PresenceEvaluator<ID>( ( PresenceNode ) node, db, schemaManager );

            case SCOPE:
                if ( ( ( ScopeNode ) node ).getScope() == SearchScope.ONELEVEL )
                {
                    return new OneLevelScopeEvaluator<ServerEntry, ID>( db, ( ScopeNode ) node );
                }
                else
                {
                    return new SubtreeScopeEvaluator<ServerEntry, ID>( db, ( ScopeNode ) node );
                }

            case SUBSTRING:
                return new SubstringEvaluator<ID>( ( SubstringNode ) node, db, schemaManager );

                /* ---------- LOGICAL OPERATORS ---------- */

            case AND:
                return buildAndEvaluator( ( AndNode ) node );

            case NOT:
                return new NotEvaluator<ID>( ( NotNode ) node, build( ( ( NotNode ) node ).getFirstChild() ) );

            case OR:
                return buildOrEvaluator( ( OrNode ) node );

                /* ----------  NOT IMPLEMENTED  ---------- */

            case ASSERTION:
            case EXTENSIBLE:
                throw new NotImplementedException();

            default:
                throw new IllegalStateException( I18n.err( I18n.ERR_260, node.getAssertionType() ) );
        }
    }


    AndEvaluator<ID> buildAndEvaluator( AndNode node ) throws Exception
    {
        List<ExprNode> children = node.getChildren();
        List<Evaluator<? extends ExprNode, ServerEntry, ID>> evaluators = new ArrayList<Evaluator<? extends ExprNode, ServerEntry, ID>>(
            children.size() );
        for ( ExprNode child : children )
        {
            evaluators.add( build( child ) );
        }
        return new AndEvaluator<ID>( node, evaluators );
    }


    OrEvaluator<ID> buildOrEvaluator( OrNode node ) throws Exception
    {
        List<ExprNode> children = node.getChildren();
        List<Evaluator<? extends ExprNode, ServerEntry, ID>> evaluators = new ArrayList<Evaluator<? extends ExprNode, ServerEntry, ID>>(
            children.size() );
        for ( ExprNode child : children )
        {
            evaluators.add( build( child ) );
        }
        return new OrEvaluator<ID>( node, evaluators );
    }
}
