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
package org.apache.directory.server.kerberos.shared.io.decoder;


import java.util.Enumeration;

import org.apache.directory.server.kerberos.shared.messages.value.PrincipalName;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERGeneralString;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587146 $, $Date: 2007-10-22 18:28:37 +0200 (Lun, 22 oct 2007) $
 */
public class PrincipalNameDecoder
{
    /**
     * Decodes a {@link DERSequence} into a {@link PrincipalName}.
     * 
     * PrincipalName ::=   SEQUENCE {
     *               name-type[0]     INTEGER,
     *               name-string[1]   SEQUENCE OF GeneralString
     * }
     * 
     * @param sequence 
     * @return The {@link PrincipalName}.
     */
    public static PrincipalName decode( DERSequence sequence )
    {
        PrincipalName principalName = new PrincipalName();

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger nameType = ( DERInteger ) derObject;
                    principalName.setNameType( nameType.intValue() );
                    break;
                    
                case 1:
                    DERSequence nameString = ( DERSequence ) derObject;
                    decodeNameString( nameString, principalName );
                    break;
            }
        }

        return principalName;
    }


    private static void decodeNameString( DERSequence sequence, PrincipalName principalName )
    {
        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERGeneralString object = ( DERGeneralString ) e.nextElement();
            principalName.addName( object.getString() );
        }
    }
}
