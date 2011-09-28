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


import java.io.IOException;
import java.util.Enumeration;

import org.apache.directory.server.kerberos.shared.messages.Encodable;
import org.apache.directory.server.kerberos.shared.messages.value.AuthorizationData;
import org.apache.directory.server.kerberos.shared.messages.value.AuthorizationDataEntry;
import org.apache.directory.server.kerberos.shared.messages.value.types.AuthorizationType;
import org.apache.directory.shared.asn1.der.ASN1InputStream;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DEROctetString;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587531 $, $Date: 2007-10-23 16:59:10 +0200 (Mar, 23 oct 2007) $
 */
public class AuthorizationDataDecoder implements Decoder, DecoderFactory
{
    public Decoder getDecoder()
    {
        return new AuthorizationDataDecoder();
    }


    public Encodable decode( byte[] encodedAuthData ) throws IOException
    {
        ASN1InputStream ais = new ASN1InputStream( encodedAuthData );

        DERSequence sequence = ( DERSequence ) ais.readObject();

        return decodeSequence( sequence );
    }


    /**
     * AuthorizationData ::=   SEQUENCE OF SEQUENCE {
     *     ad-type[0]               INTEGER,
     *     ad-data[1]               OCTET STRING
     * }
     */
    protected static AuthorizationData decodeSequence( DERSequence sequence )
    {
        AuthorizationData authData = new AuthorizationData();

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERSequence object = ( DERSequence ) e.nextElement();
            AuthorizationDataEntry entry = decodeAuthorizationEntry( object );
            authData.add( entry );
        }

        return authData;
    }


    protected static AuthorizationDataEntry decodeAuthorizationEntry( DERSequence sequence )
    {
        AuthorizationType type = AuthorizationType.NULL;
        byte[] data = null;

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger tag0 = ( DERInteger ) derObject;
                    type = AuthorizationType.getTypeByOrdinal( tag0.intValue() );
                    break;
                    
                case 1:
                    DEROctetString tag1 = ( DEROctetString ) derObject;
                    data = tag1.getOctets();
                    break;
            }
        }

        return new AuthorizationDataEntry( type, data );
    }
}
