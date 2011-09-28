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

import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.shared.asn1.der.ASN1InputStream;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DEROctetString;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 540371 $, $Date: 2007-05-22 02:00:43 +0200 (Mar, 22 mai 2007) $
 */
public class EncryptionKeyDecoder
{
    /**
     * Decodes a byte array into an {@link EncryptionKey}.
     *
     * @param encodedEncryptionKey
     * @return The {@link EncryptionKey}.
     * @throws IOException
     */
    public static EncryptionKey decode( byte[] encodedEncryptionKey ) throws IOException
    {
        ASN1InputStream ais = new ASN1InputStream( encodedEncryptionKey );

        DERSequence sequence = ( DERSequence ) ais.readObject();

        return decode( sequence );
    }


    /**
     * EncryptionKey ::=   SEQUENCE {
     *     keytype[0]    INTEGER,
     *     keyvalue[1]   OCTET STRING
     * }
     */
    protected static EncryptionKey decode( DERSequence sequence )
    {
        EncryptionType type = EncryptionType.NULL;
        byte[] data = null;

        for ( Enumeration e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger tag0 = ( DERInteger ) derObject;
                    type = EncryptionType.getTypeByOrdinal( tag0.intValue() );
                    break;
                case 1:
                    DEROctetString tag1 = ( DEROctetString ) derObject;
                    data = tag1.getOctets();
                    break;
            }
        }

        return new EncryptionKey( type, data );
    }
}
