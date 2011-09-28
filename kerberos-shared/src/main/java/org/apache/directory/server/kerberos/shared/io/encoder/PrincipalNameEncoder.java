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
package org.apache.directory.server.kerberos.shared.io.encoder;


import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.security.auth.kerberos.KerberosPrincipal;

import org.apache.directory.server.kerberos.shared.messages.value.PrincipalName;
import org.apache.directory.shared.asn1.der.DERGeneralString;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587146 $, $Date: 2007-10-22 18:28:37 +0200 (Lun, 22 oct 2007) $
 */
public class PrincipalNameEncoder
{
    private static final String COMPONENT_SEPARATOR = "/";
    private static final String REALM_SEPARATOR = "@";


    /**
     * Encodes a {@link KerberosPrincipal} into a {@link DERSequence}.
     * 
     * PrincipalName ::=   SEQUENCE {
     *               name-type[0]     INTEGER,
     *               name-string[1]   SEQUENCE OF GeneralString
     * }
     * 
     * @param principal 
     * @return The {@link DERSequence}.
     */
    public static DERSequence encode( KerberosPrincipal principal )
    {
        DERSequence vector = new DERSequence();

        vector.add( new DERTaggedObject( 0, DERInteger.valueOf( principal.getNameType() ) ) );
        vector.add( new DERTaggedObject( 1, encodeNameSequence( principal ) ) );

        return vector;
    }


    /**
     * Encodes a {@link PrincipalName} into a {@link DERSequence}.
     *
     * @param name
     * @return The {@link DERSequence}.
     */
    public static DERSequence encode( PrincipalName name )
    {
        DERSequence vector = new DERSequence();

        vector.add( new DERTaggedObject( 0, DERInteger.valueOf( name.getNameType().getOrdinal() ) ) );
        vector.add( new DERTaggedObject( 1, encodeNameSequence( name ) ) );

        return vector;
    }


    private static DERSequence encodeNameSequence( KerberosPrincipal principal )
    {
        Iterator<String> it = getNameStrings( principal ).iterator();

        DERSequence vector = new DERSequence();

        while ( it.hasNext() )
        {
            vector.add( DERGeneralString.valueOf( it.next() ) );
        }

        return vector;
    }


    private static List<String> getNameStrings( KerberosPrincipal principal )
    {
        String nameComponent = principal.getName().split( REALM_SEPARATOR )[0];
        String[] components = nameComponent.split( COMPONENT_SEPARATOR );
        return Arrays.asList( components );
    }


    private static DERSequence encodeNameSequence( PrincipalName principalName )
    {
        DERSequence vector = new DERSequence();

        for ( String name:principalName.getNames() )
        {
            vector.add( DERGeneralString.valueOf( name ) );
        }

        return vector;
    }
}
